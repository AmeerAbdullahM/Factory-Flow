package com.factoryflow.dispatch;

import com.factoryflow.audit.AuditService;
import com.factoryflow.common.BusinessException;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.inventory.StockMovement;
import com.factoryflow.invoicing.Invoice;
import com.factoryflow.invoicing.InvoiceItem;
import com.factoryflow.invoicing.InvoiceRepository;
import com.factoryflow.products.Product;
import com.factoryflow.products.ProductRepository;
import com.factoryflow.sales.SalesOrder;
import com.factoryflow.sales.SalesOrderRepository;
import com.factoryflow.users.User;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DispatchService {

    private final DispatchRepository dispatchRepo;
    private final InvoiceRepository invoiceRepo;
    private final ProductRepository productRepo;
    private final SalesOrderRepository orderRepo;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public DispatchService(DispatchRepository dispatchRepo, InvoiceRepository invoiceRepo,
                           ProductRepository productRepo, SalesOrderRepository orderRepo,
                           InventoryService inventoryService, AuditService auditService) {
        this.dispatchRepo = dispatchRepo;
        this.invoiceRepo = invoiceRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    public List<DispatchDto> getAll() {
        return dispatchRepo.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    /**
     * Record dispatch in ONE atomic transaction:
     * 1. Validate invoice is ACTIVE
     * 2. Deduct FG stock via InventoryService (prevents negative stock)
     * 3. Update sales order status to DISPATCHED or PARTIALLY_DISPATCHED
     * 4. Audit log
     */
    @Transactional
    public DispatchDto createDispatch(CreateDispatchRequest req, User user) {
        Invoice invoice = invoiceRepo.findById(req.invoiceId())
                .orElseThrow(() -> new BusinessException("Invoice not found", HttpStatus.NOT_FOUND));

        if (!"ACTIVE".equals(invoice.getStatus())) {
            throw new BusinessException("Cannot dispatch a cancelled invoice", HttpStatus.CONFLICT);
        }

        Dispatch dispatch = new Dispatch();
        dispatch.setDispatchNumber("DSP-" + System.currentTimeMillis());
        dispatch.setInvoice(invoice);
        dispatch.setCustomer(invoice.getCustomer());
        dispatch.setDispatchDate(req.dispatchDate() != null ? req.dispatchDate() : LocalDate.now());
        dispatch.setTransportDetails(req.transportDetails());
        dispatch.setDeliveryReference(req.deliveryReference());
        dispatch.setNotes(req.notes());
        dispatch.setStatus("DISPATCHED");
        dispatch.setCreatedBy(user);

        List<DispatchItem> items = new ArrayList<>();
        for (DispatchItemRequest itemReq : req.items()) {
            Product product = productRepo.findById(itemReq.productId())
                    .orElseThrow(() -> new BusinessException("Product not found", HttpStatus.NOT_FOUND));

            InvoiceItem invItem = invoice.getItems().stream()
                    .filter(ii -> ii.getProduct().getId().equals(product.getId()))
                    .findFirst()
                    .orElse(null);

            StockMovement sm = inventoryService.dispatchFinishedGoods(
                    product, BigDecimal.valueOf(itemReq.quantity()),
                    "DISPATCH", invoice.getId(), invoice.getInvoiceNumber(), user);

            DispatchItem di = new DispatchItem();
            di.setDispatch(dispatch);
            di.setInvoiceItem(invItem);
            di.setProduct(product);
            di.setQuantity(itemReq.quantity());
            di.setStockMovement(sm);
            items.add(di);
        }
        dispatch.setItems(items);

        Dispatch saved = dispatchRepo.save(dispatch);

        if (invoice.getSalesOrder() != null) {
            SalesOrder order = invoice.getSalesOrder();
            int totalOrdered = order.getItems().stream().mapToInt(i -> i.getOrderedQuantity()).sum();
            int totalDispatched = dispatchRepo.findByInvoice_Id(invoice.getId()).stream()
                    .flatMap(d -> d.getItems().stream())
                    .mapToInt(DispatchItem::getQuantity).sum();
            order.setStatus(totalDispatched >= totalOrdered ? "DISPATCHED" : "PARTIALLY_DISPATCHED");
            orderRepo.save(order);
        }

        auditService.log(user, "DISPATCH_RECORDED", "DISPATCH", saved.getId(),
                null, Map.of("dispatchNumber", saved.getDispatchNumber(), "invoiceNumber", invoice.getInvoiceNumber()),
                saved.getDispatchNumber());

        return toDto(saved);
    }

    public DispatchDto toDto(Dispatch d) {
        List<DispatchItemDto> itemDtos = d.getItems().stream().map(i -> new DispatchItemDto(
                i.getId(), i.getProduct().getId(), i.getProduct().getName(), i.getQuantity()
        )).toList();

        return new DispatchDto(
                d.getId(), d.getDispatchNumber(), d.getInvoice().getId(), d.getInvoice().getInvoiceNumber(),
                d.getCustomer().getId(), d.getCustomer().getName(), d.getDispatchDate(),
                d.getTransportDetails(), d.getDeliveryReference(), d.getStatus(), itemDtos
        );
    }

    public record DispatchDto(UUID id, String dispatchNumber, UUID invoiceId, String invoiceNumber,
                              UUID customerId, String customerName, LocalDate dispatchDate,
                              String transportDetails, String deliveryReference, String status,
                              List<DispatchItemDto> items) {}

    public record DispatchItemDto(UUID id, UUID productId, String productName, int quantity) {}

    public record CreateDispatchRequest(
            @NotNull UUID invoiceId, LocalDate dispatchDate,
            String transportDetails, String deliveryReference, String notes,
            @NotEmpty List<DispatchItemRequest> items) {}

    public record DispatchItemRequest(@NotNull UUID productId, @Min(1) int quantity) {}
}
