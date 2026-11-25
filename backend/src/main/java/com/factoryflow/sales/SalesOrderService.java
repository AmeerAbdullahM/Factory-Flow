package com.factoryflow.sales;

import com.factoryflow.audit.AuditService;
import com.factoryflow.common.BusinessException;
import com.factoryflow.common.GstCalculator;
import com.factoryflow.customers.Customer;
import com.factoryflow.customers.CustomerRepository;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.products.Product;
import com.factoryflow.products.ProductRepository;
import com.factoryflow.users.User;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SalesOrderService {

    private final SalesOrderRepository orderRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public SalesOrderService(SalesOrderRepository orderRepo, CustomerRepository customerRepo,
                             ProductRepository productRepo, InventoryService inventoryService,
                             AuditService auditService) {
        this.orderRepo = orderRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    public List<SalesOrderDto> getOrdersForUser(User user, String status) {
        List<SalesOrder> orders;
        if (user.hasRole("SALESMAN") && !user.hasRole("ADMIN") && !user.hasRole("OWNER")) {
            orders = orderRepo.findBySalesman_IdOrderByCreatedAtDesc(user.getId());
        } else if (status != null && !status.isBlank()) {
            orders = orderRepo.findByStatusOrderByCreatedAtDesc(status);
        } else {
            orders = orderRepo.findAllByOrderByCreatedAtDesc();
        }
        if (status != null && !status.isBlank()) {
            orders = orders.stream().filter(o -> status.equals(o.getStatus())).toList();
        }
        return orders.stream().map(this::toDto).toList();
    }

    public SalesOrderDto getOrder(UUID id, User user) {
        SalesOrder o = getEntity(id);
        if (user.hasRole("SALESMAN") && !user.hasRole("ADMIN") && !user.hasRole("OWNER")) {
            if (!o.getSalesman().getId().equals(user.getId())) {
                throw new BusinessException("Access denied to order", HttpStatus.FORBIDDEN);
            }
        }
        return toDto(o);
    }

    public SalesOrder getEntity(UUID id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Sales order not found: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public SalesOrderDto createOrder(CreateOrderRequest req, User salesman) {
        Customer customer = customerRepo.findById(req.customerId())
                .orElseThrow(() -> new BusinessException("Customer not found", HttpStatus.NOT_FOUND));

        SalesOrder order = new SalesOrder();
        order.setOrderNumber(req.orderNumber() != null && !req.orderNumber().isBlank()
                ? req.orderNumber()
                : "SO-" + System.currentTimeMillis());
        order.setCustomer(customer);
        order.setSalesman(salesman);
        order.setOrderDate(req.orderDate() != null ? req.orderDate() : LocalDate.now());
        order.setStatus("SUBMITTED");
        order.setNotes(req.notes());

        List<SalesOrderItem> items = new ArrayList<>();
        for (OrderItemRequest itemReq : req.items()) {
            Product product = productRepo.findById(itemReq.productId())
                    .orElseThrow(() -> new BusinessException("Product not found", HttpStatus.NOT_FOUND));

            SalesOrderItem soi = new SalesOrderItem();
            soi.setSalesOrder(order);
            soi.setProduct(product);
            soi.setOrderedQuantity(itemReq.quantity());
            soi.setApprovedQuantity(itemReq.quantity());
            BigDecimal price = itemReq.unitPrice() != null ? itemReq.unitPrice() : product.getSellingPrice();
            soi.setUnitPrice(price);
            soi.setGstRate(product.getGstRate());

            BigDecimal taxable = price.multiply(BigDecimal.valueOf(itemReq.quantity())).setScale(2, RoundingMode.HALF_UP);
            var gst = GstCalculator.compute(taxable, product.getGstRate(), customer.getStateCode());
            soi.setTaxableAmount(taxable);
            soi.setGstAmount(gst.cgst().add(gst.sgst()).add(gst.igst()));
            soi.setTotalAmount(gst.total());
            items.add(soi);
        }
        order.setItems(items);

        SalesOrder saved = orderRepo.save(order);
        auditService.log(salesman, "SALES_ORDER_CREATED", "SALES_ORDER", saved.getId(),
                null, Map.of("orderNumber", saved.getOrderNumber(), "customer", customer.getName()),
                saved.getOrderNumber());

        return toDto(saved);
    }

    public List<StockCheckResult> getStockAvailability(UUID orderId) {
        SalesOrder order = getEntity(orderId);
        return order.getItems().stream().map(item -> {
            int reqQty = item.getApprovedQuantity() != null ? item.getApprovedQuantity() : item.getOrderedQuantity();
            BigDecimal available = inventoryService.getCurrentStock("FINISHED_GOODS", null, null, item.getProduct().getId());
            BigDecimal reqBd = BigDecimal.valueOf(reqQty);
            boolean sufficient = available.compareTo(reqBd) >= 0;
            BigDecimal shortage = sufficient ? BigDecimal.ZERO : reqBd.subtract(available);
            return new StockCheckResult(
                    item.getId(), item.getProduct().getId(), item.getProduct().getName(),
                    reqQty, available, sufficient, shortage
            );
        }).toList();
    }

    /**
     * Admin approves an order.
     * Approving does NOT reduce FG stock (dispatch does).
     * Admin may adjust approved quantities (e.g. for partial fulfillment when stock is short);
     * adjustments are audit-logged.
     */
    @Transactional
    public SalesOrderDto approveOrder(UUID orderId, ApproveOrderRequest req, User admin) {
        SalesOrder order = getEntity(orderId);
        if (!"SUBMITTED".equals(order.getStatus())) {
            throw new BusinessException("Only SUBMITTED orders can be approved. Current status: " + order.getStatus(),
                    HttpStatus.CONFLICT);
        }

        if (req != null && req.adjustments() != null) {
            for (ApprovedQtyAdjustment adj : req.adjustments()) {
                SalesOrderItem item = order.getItems().stream()
                        .filter(i -> i.getId().equals(adj.orderItemId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("Order item not found: " + adj.orderItemId(),
                                HttpStatus.NOT_FOUND));
                int oldQty = item.getApprovedQuantity() != null ? item.getApprovedQuantity() : item.getOrderedQuantity();
                item.setApprovedQuantity(adj.approvedQuantity());
                if (adj.unitPrice() != null) {
                    item.setUnitPrice(adj.unitPrice());
                }
                BigDecimal taxable = item.getUnitPrice().multiply(BigDecimal.valueOf(adj.approvedQuantity()))
                        .setScale(2, RoundingMode.HALF_UP);
                var gst = GstCalculator.compute(taxable, item.getGstRate(), order.getCustomer().getStateCode());
                item.setTaxableAmount(taxable);
                item.setGstAmount(gst.cgst().add(gst.sgst()).add(gst.igst()));
                item.setTotalAmount(gst.total());

                if (oldQty != adj.approvedQuantity()) {
                    auditService.log(admin, "SALES_ORDER_QTY_ADJUSTED", "SALES_ORDER", order.getId(),
                            Map.of("product", item.getProduct().getName(), "qty", oldQty),
                            Map.of("product", item.getProduct().getName(), "qty", adj.approvedQuantity()),
                            order.getOrderNumber());
                }
            }
        }

        order.setStatus("APPROVED");
        order.setApprovedBy(admin);
        order.setApprovedAt(Instant.now());
        SalesOrder saved = orderRepo.save(order);

        auditService.log(admin, "SALES_ORDER_APPROVED", "SALES_ORDER", saved.getId(),
                Map.of("status", "SUBMITTED"), Map.of("status", "APPROVED"), saved.getOrderNumber());

        return toDto(saved);
    }

    @Transactional
    public SalesOrderDto rejectOrder(UUID orderId, String reason, User admin) {
        SalesOrder order = getEntity(orderId);
        if (!"SUBMITTED".equals(order.getStatus())) {
            throw new BusinessException("Only SUBMITTED orders can be rejected", HttpStatus.CONFLICT);
        }
        order.setStatus("REJECTED");
        order.setRejectionReason(reason);
        SalesOrder saved = orderRepo.save(order);

        auditService.log(admin, "SALES_ORDER_REJECTED", "SALES_ORDER", saved.getId(),
                Map.of("status", "SUBMITTED"), Map.of("status", "REJECTED", "reason", reason != null ? reason : ""),
                saved.getOrderNumber());

        return toDto(saved);
    }

    public SalesOrderDto toDto(SalesOrder o) {
        List<SalesOrderItemDto> itemDtos = o.getItems().stream().map(i -> new SalesOrderItemDto(
                i.getId(), i.getProduct().getId(), i.getProduct().getName(),
                i.getOrderedQuantity(), i.getApprovedQuantity(), i.getUnitPrice(),
                i.getGstRate(), i.getTaxableAmount(), i.getGstAmount(), i.getTotalAmount()
        )).toList();

        BigDecimal totalTaxable = itemDtos.stream().map(i -> i.taxableAmount() != null ? i.taxableAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGst = itemDtos.stream().map(i -> i.gstAmount() != null ? i.gstAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grandTotal = itemDtos.stream().map(i -> i.totalAmount() != null ? i.totalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SalesOrderDto(
                o.getId(), o.getOrderNumber(), o.getCustomer().getId(), o.getCustomer().getName(),
                o.getCustomer().getType(), o.getCustomer().getState(),
                o.getSalesman().getId(), o.getSalesman().getFullName(),
                o.getOrderDate(), o.getStatus(), o.getNotes(), o.getRejectionReason(),
                itemDtos, totalTaxable, totalGst, grandTotal
        );
    }

    public record SalesOrderDto(
            UUID id, String orderNumber, UUID customerId, String customerName,
            String customerType, String customerState, UUID salesmanId, String salesmanName,
            LocalDate orderDate, String status, String notes, String rejectionReason,
            List<SalesOrderItemDto> items, BigDecimal totalTaxable, BigDecimal totalGst, BigDecimal grandTotal) {}

    public record SalesOrderItemDto(
            UUID id, UUID productId, String productName, int orderedQuantity,
            Integer approvedQuantity, BigDecimal unitPrice, BigDecimal gstRate,
            BigDecimal taxableAmount, BigDecimal gstAmount, BigDecimal totalAmount) {}

    public record CreateOrderRequest(
            @NotNull UUID customerId, LocalDate orderDate, String orderNumber, String notes,
            @NotEmpty List<OrderItemRequest> items) {}

    public record OrderItemRequest(@NotNull UUID productId, @Min(1) int quantity, BigDecimal unitPrice) {}

    public record StockCheckResult(
            UUID orderItemId, UUID productId, String productName, int requiredQty,
            BigDecimal availableQty, boolean sufficient, BigDecimal shortageQty) {}

    public record ApproveOrderRequest(List<ApprovedQtyAdjustment> adjustments) {}

    public record ApprovedQtyAdjustment(@NotNull UUID orderItemId, @Min(0) int approvedQuantity, BigDecimal unitPrice) {}
}
