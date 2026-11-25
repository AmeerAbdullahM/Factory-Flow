package com.factoryflow;

import com.factoryflow.common.BusinessException;
import com.factoryflow.dispatch.DispatchService;
import com.factoryflow.dispatch.DispatchService.CreateDispatchRequest;
import com.factoryflow.dispatch.DispatchService.DispatchItemRequest;
import com.factoryflow.inventory.InventoryBalance;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.inventory.StockMovement;
import com.factoryflow.invoicing.InvoiceService;
import com.factoryflow.invoicing.InvoiceService.CreateInvoiceRequest;
import com.factoryflow.invoicing.InvoiceService.InvoiceDto;
import com.factoryflow.payments.PaymentService;
import com.factoryflow.payments.PaymentService.CreatePaymentRequest;
import com.factoryflow.production.ProductionService;
import com.factoryflow.production.ProductionService.*;
import com.factoryflow.purchases.PurchaseService;
import com.factoryflow.purchases.PurchaseService.*;
import com.factoryflow.reports.ReportService;
import com.factoryflow.sales.SalesOrderService;
import com.factoryflow.sales.SalesOrderService.*;
import com.factoryflow.users.User;
import com.factoryflow.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FullBenchmarkEndToEndTest {

    @Autowired private PurchaseService purchaseService;
    @Autowired private ProductionService productionService;
    @Autowired private InventoryService inventoryService;
    @Autowired private SalesOrderService salesOrderService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private DispatchService dispatchService;
    @Autowired private PaymentService paymentService;
    @Autowired private ReportService reportService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final UUID SUPPLIER_FLOUR_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
    private static final UUID RAW_FLOUR_ID = UUID.fromString("55555555-0000-0000-0000-000000000001");
    private static final UUID RAW_OIL_ID = UUID.fromString("55555555-0000-0000-0000-000000000002");
    private static final UUID RAW_SPICE_ID = UUID.fromString("55555555-0000-0000-0000-000000000003");
    private static final UUID PKG_POUCH_ID = UUID.fromString("66666666-0000-0000-0000-000000000001");
    private static final UUID PKG_CARTON_ID = UUID.fromString("66666666-0000-0000-0000-000000000002");
    private static final UUID UNIT_KG_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_MURUKKU_ID = UUID.fromString("77777777-0000-0000-0000-000000000001");
    private static final UUID RETAILER_A_ID = UUID.fromString("88888888-0000-0000-0000-000000000001");
    private static final UUID DISTRIBUTOR_B_ID = UUID.fromString("88888888-0000-0000-0000-000000000002");

    @Test
    void seedUsersHaveWorkingPasswordHash() {
        for (String uname : List.of("salesman_a", "salesman_b", "admin", "supervisor", "auditor", "owner")) {
            User u = userRepository.findByUsername(uname).orElseThrow();
            assertTrue(passwordEncoder.matches("password123", u.getPasswordHash()),
                    "Seeded password123 should match for " + uname);
        }
    }

    @Test
    void fullSection21BenchmarkEndToEnd() {
        User salesmanA = userRepository.findByUsername("salesman_a").orElseThrow();
        User salesmanB = userRepository.findByUsername("salesman_b").orElseThrow();
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User supervisor = userRepository.findByUsername("supervisor").orElseThrow();

        LocalDate today = LocalDate.of(2026, 11, 25);

        // 1. Purchase 60 kg flour @ ₹45 -> flour 160 kg @ ₹43.125
        PurchaseDto pur = purchaseService.createPurchase(new CreatePurchaseRequest(
                SUPPLIER_FLOUR_ID, today, "PUR-BM-001", "Benchmark flour",
                List.of(new CreatePurchaseItemRequest("RAW_MATERIAL", RAW_FLOUR_ID, null,
                        UNIT_KG_ID, new BigDecimal("60"), new BigDecimal("45"), BigDecimal.ZERO))
        ), admin);
        purchaseService.receivePurchase(pur.id(), admin);

        // 2. Start production run of Murukku 200g (1 batch = 200 packets)
        ProductionRunDto run = productionService.createRun(new CreateProductionRunRequest(
                PRODUCT_MURUKKU_ID, null, 1, today, "PRD-BM-001", "Benchmark run"
        ), supervisor);

        // 3. Record actuals & post
        List<ActualMaterialEntry> entries = run.materialIssues().stream().map(mi -> {
            if (mi.itemName().contains("Flour")) return new ActualMaterialEntry(mi.id(), new BigDecimal("40"), BigDecimal.ZERO);
            if (mi.itemName().contains("Oil")) return new ActualMaterialEntry(mi.id(), new BigDecimal("10.5"), BigDecimal.ZERO);
            if (mi.itemName().contains("Spice")) return new ActualMaterialEntry(mi.id(), new BigDecimal("1.2"), BigDecimal.ZERO);
            if (mi.itemName().contains("Pouch")) return new ActualMaterialEntry(mi.id(), new BigDecimal("188"), new BigDecimal("6"));
            return new ActualMaterialEntry(mi.id(), new BigDecimal("9"), BigDecimal.ZERO);
        }).toList();
        productionService.updateActuals(run.id(), new UpdateActualsRequest(188, 6, entries, "Actuals"), supervisor);
        ProductionRunDto postedRun = productionService.postRun(run.id(), supervisor);

        assertEquals(0, new BigDecimal("238").compareTo(
                inventoryService.getCurrentStock("FINISHED_GOODS", null, null, PRODUCT_MURUKKU_ID)));

        // 4. Salesman A orders 150 packets for Retailer A; Admin sees 238 available & approves
        SalesOrderDto orderA = salesOrderService.createOrder(new CreateOrderRequest(
                RETAILER_A_ID, today, "SO-BM-A", "Retailer A order",
                List.of(new OrderItemRequest(PRODUCT_MURUKKU_ID, 150, null))
        ), salesmanA);
        var checkA = salesOrderService.getStockAvailability(orderA.id());
        assertTrue(checkA.get(0).sufficient());
        assertEquals(0, new BigDecimal("238").compareTo(checkA.get(0).availableQty()));
        salesOrderService.approveOrder(orderA.id(), null, admin);

        // Salesman B cannot see Salesman A's order
        assertThrows(BusinessException.class, () -> salesOrderService.getOrder(orderA.id(), salesmanB));

        // 5. Admin raises GST invoice (CGST+SGST) and dispatches 150 -> FG 88
        InvoiceDto invA = invoiceService.createInvoice(new CreateInvoiceRequest(
                orderA.id(), today, "30 days", "Retailer A invoice"), admin);
        assertFalse(invA.interstate());
        assertEquals(0, new BigDecimal("7500.00").compareTo(invA.taxableAmount()));
        assertEquals(0, new BigDecimal("187.50").compareTo(invA.cgstAmount()));
        assertEquals(0, new BigDecimal("187.50").compareTo(invA.sgstAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(invA.igstAmount()));
        assertEquals(0, new BigDecimal("7875.00").compareTo(invA.totalAmount()));

        byte[] invPdf = invoiceService.generatePdf(invA.id());
        assertTrue(invPdf.length > 500, "Invoice PDF should be generated");

        dispatchService.createDispatch(new CreateDispatchRequest(
                invA.id(), today, "TN-01-AB-1234", "LR-001", "Full dispatch",
                List.of(new DispatchItemRequest(PRODUCT_MURUKKU_ID, 150))
        ), admin);

        assertEquals(0, new BigDecimal("88").compareTo(
                inventoryService.getCurrentStock("FINISHED_GOODS", null, null, PRODUCT_MURUKKU_ID)));

        // 6. Salesman B orders 120 packets for Distributor B -> shortage 32 flagged, full invoice blocked
        SalesOrderDto orderB = salesOrderService.createOrder(new CreateOrderRequest(
                DISTRIBUTOR_B_ID, today, "SO-BM-B", "Distributor B order",
                List.of(new OrderItemRequest(PRODUCT_MURUKKU_ID, 120, null))
        ), salesmanB);
        var checkB = salesOrderService.getStockAvailability(orderB.id());
        assertFalse(checkB.get(0).sufficient());
        assertEquals(0, new BigDecimal("88").compareTo(checkB.get(0).availableQty()));
        assertEquals(0, new BigDecimal("32").compareTo(checkB.get(0).shortageQty()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(orderB.totalTaxable()));
        assertEquals(0, new BigDecimal("300.00").compareTo(orderB.totalGst())); // 5% IGST on 6000

        salesOrderService.approveOrder(orderB.id(), null, admin);
        // Full invoice for 120 when only 88 available MUST be blocked
        assertThrows(BusinessException.class, () -> invoiceService.createInvoice(
                new CreateInvoiceRequest(orderB.id(), today, "45 days", "Should block"), admin));

        // 7. Record ₹4,000 payment from Retailer A -> ₹3,875 outstanding
        paymentService.recordPayment(new CreatePaymentRequest(
                RETAILER_A_ID, today, "UPI", new BigDecimal("4000.00"),
                "PAY-BM-001", "UTR12345", "Part payment", null
        ), admin);
        var outstandingA = paymentService.getOutstandingForCustomer(RETAILER_A_ID);
        assertEquals(1, outstandingA.size());
        assertEquals(0, new BigDecimal("3875.00").compareTo(outstandingA.get(0).outstanding()));

        // 8. Add Labour ₹1,200 + Gas/Power ₹600 after posting -> ₹5,855 / 188 ≈ ₹31.14
        productionService.addOverheadCost(postedRun.id(), new AddCostRequest("LABOUR", "Shift labour", new BigDecimal("1200")), supervisor);
        ProductionRunDto finalRun = productionService.addOverheadCost(postedRun.id(), new AddCostRequest("GAS", "Gas & power", new BigDecimal("600")), supervisor);
        assertEquals(0, new BigDecimal("5855.00").compareTo(finalRun.totalCost()));
        assertEquals(0, new BigDecimal("31.1436").compareTo(finalRun.costPerPacket()));

        // 9. Generate reports & verify exports (CSV, Excel, PDF) + ledger reconciliation
        assertFalse(reportService.getSalesRegister(null, null, null).isEmpty());
        assertFalse(reportService.getGstSummary(null, null).isEmpty());
        assertFalse(reportService.getPurchaseRegister(null, null).isEmpty());
        assertFalse(reportService.getStockSummary().isEmpty());
        assertFalse(reportService.getStockLedger().isEmpty());
        assertFalse(reportService.getProductionReport(null, null).isEmpty());
        assertFalse(reportService.getCollections(null, null).isEmpty());

        byte[] xlsx = reportService.exportExcel("Stock", List.of("Item", "Qty"), List.of(List.of("Flour", "120")));
        assertTrue(xlsx.length > 100);
        byte[] csv = reportService.exportCsv(List.of("Item", "Qty"), List.of(List.of("Flour", "120")));
        assertTrue(new String(csv).contains("Flour,120"));

        // Final stock assertions from Section 21
        InventoryBalance flourBal = inventoryService.getBalance("RAW_MATERIAL", RAW_FLOUR_ID, null, null).orElseThrow();
        assertEquals(0, new BigDecimal("120").compareTo(flourBal.getCurrentQuantity()));
        assertEquals(0, new BigDecimal("5175.00").compareTo(flourBal.getTotalValue()));
        assertEquals(0, new BigDecimal("19.5").compareTo(inventoryService.getCurrentStock("RAW_MATERIAL", RAW_OIL_ID, null, null)));
        assertEquals(0, new BigDecimal("3.8").compareTo(inventoryService.getCurrentStock("RAW_MATERIAL", RAW_SPICE_ID, null, null)));
        assertEquals(0, new BigDecimal("306").compareTo(inventoryService.getCurrentStock("PACKAGING", null, PKG_POUCH_ID, null)));
        assertEquals(0, new BigDecimal("21").compareTo(inventoryService.getCurrentStock("PACKAGING", null, PKG_CARTON_ID, null)));
        assertEquals(0, new BigDecimal("88").compareTo(inventoryService.getCurrentStock("FINISHED_GOODS", null, null, PRODUCT_MURUKKU_ID)));

        assertTrue(inventoryService.reconcileAll());
    }
}
