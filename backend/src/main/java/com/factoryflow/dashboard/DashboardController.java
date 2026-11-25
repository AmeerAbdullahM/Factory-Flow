package com.factoryflow.dashboard;

import com.factoryflow.inventory.InventoryBalance;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.invoicing.Invoice;
import com.factoryflow.invoicing.InvoiceRepository;
import com.factoryflow.payments.Payment;
import com.factoryflow.payments.PaymentRepository;
import com.factoryflow.payments.PaymentService;
import com.factoryflow.production.ProductionRun;
import com.factoryflow.production.ProductionRunRepository;
import com.factoryflow.sales.SalesOrder;
import com.factoryflow.sales.SalesOrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final SalesOrderRepository orderRepo;
    private final InvoiceRepository invoiceRepo;
    private final InventoryService inventoryService;
    private final ProductionRunRepository runRepo;
    private final PaymentRepository paymentRepo;
    private final PaymentService paymentService;

    public DashboardController(SalesOrderRepository orderRepo, InvoiceRepository invoiceRepo,
                               InventoryService inventoryService, ProductionRunRepository runRepo,
                               PaymentRepository paymentRepo, PaymentService paymentService) {
        this.orderRepo = orderRepo;
        this.invoiceRepo = invoiceRepo;
        this.inventoryService = inventoryService;
        this.runRepo = runRepo;
        this.paymentRepo = paymentRepo;
        this.paymentService = paymentService;
    }

    @GetMapping("/owner")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','ACCOUNTANT')")
    public ResponseEntity<OwnerDashboardDto> ownerDashboard() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        List<SalesOrder> allOrders = orderRepo.findAll();
        long todaysOrders = allOrders.stream().filter(o -> today.equals(o.getOrderDate())).count();
        long pendingOrders = allOrders.stream().filter(o -> "SUBMITTED".equals(o.getStatus())).count();
        long dispatchedOrders = allOrders.stream().filter(o -> "DISPATCHED".equals(o.getStatus())).count();

        BigDecimal monthlySales = invoiceRepo.findByInvoiceDateBetween(monthStart, today).stream()
                .filter(i -> "ACTIVE".equals(i.getStatus()))
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollections = paymentRepo.findAll().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var outstandingList = paymentService.getAllOutstanding();
        BigDecimal totalReceivables = outstandingList.stream()
                .map(PaymentService.OutstandingDto::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long overdueCount = outstandingList.stream().filter(PaymentService.OutstandingDto::overdue).count();

        List<InventoryBalance> balances = inventoryService.getAllBalances();
        BigDecimal rawStockValue = balances.stream()
                .filter(b -> "RAW_MATERIAL".equals(b.getItemType()))
                .map(InventoryBalance::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fgStockPackets = balances.stream()
                .filter(b -> "FINISHED_GOODS".equals(b.getItemType()))
                .map(InventoryBalance::getCurrentQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> alerts = new ArrayList<>();
        for (InventoryBalance b : balances) {
            if ("RAW_MATERIAL".equals(b.getItemType()) && b.getRawMaterial() != null
                    && b.getCurrentQuantity().compareTo(b.getRawMaterial().getReorderLevel()) <= 0) {
                alerts.add("Low raw material: " + b.getRawMaterial().getName() + " (" + b.getCurrentQuantity() + " " + b.getRawMaterial().getUnit().getSymbol() + ")");
            }
            if ("PACKAGING".equals(b.getItemType()) && b.getPackagingMaterial() != null
                    && b.getCurrentQuantity().compareTo(b.getPackagingMaterial().getReorderLevel()) <= 0) {
                alerts.add("Low packaging: " + b.getPackagingMaterial().getName() + " (" + b.getCurrentQuantity() + ")");
            }
        }

        List<ProductionRun> postedRuns = runRepo.findByStatus("POSTED");
        BigDecimal latestCostPerPacket = postedRuns.isEmpty() ? null : postedRuns.get(postedRuns.size() - 1).getCostPerPacket();
        long varianceAlertsCount = 0;
        for (ProductionRun r : postedRuns) {
            if (r.isYieldFlagged()) {
                varianceAlertsCount++;
                alerts.add("Yield variance on " + r.getReferenceNumber() + ": " + r.getYieldVariancePercent() + "%");
            }
            for (var mi : r.getMaterialIssues()) {
                if (mi.isVarianceFlagged()) {
                    varianceAlertsCount++;
                    String name = mi.getRawMaterial() != null ? mi.getRawMaterial().getName() : mi.getPackagingMaterial().getName();
                    alerts.add("Material variance on " + r.getReferenceNumber() + " (" + name + "): " + mi.getVariancePercent() + "%");
                }
            }
        }
        if (overdueCount > 0) {
            alerts.add(overdueCount + " overdue customer invoice(s)");
        }

        return ResponseEntity.ok(new OwnerDashboardDto(
                todaysOrders, pendingOrders, dispatchedOrders, monthlySales,
                totalCollections, totalReceivables, overdueCount,
                rawStockValue, fgStockPackets, latestCostPerPacket,
                varianceAlertsCount, alerts
        ));
    }

    public record OwnerDashboardDto(
            long todaysOrders, long pendingOrders, long dispatchedOrders,
            BigDecimal monthlySales, BigDecimal totalCollections, BigDecimal totalReceivables,
            long overdueInvoices, BigDecimal rawStockValue, BigDecimal fgStockPackets,
            BigDecimal latestCostPerPacket, long varianceAlertsCount, List<String> alerts) {}
}
