package com.factoryflow;

import com.factoryflow.inventory.InventoryBalance;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.inventory.StockMovement;
import com.factoryflow.production.ProductionService;
import com.factoryflow.production.ProductionService.*;
import com.factoryflow.purchases.PurchaseService;
import com.factoryflow.purchases.PurchaseService.*;
import com.factoryflow.users.User;
import com.factoryflow.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional // Rolls back after test so seeded DB remains clean
class ProductionBenchmarkPhase4Test {

    @Autowired private PurchaseService purchaseService;
    @Autowired private ProductionService productionService;
    @Autowired private InventoryService inventoryService;
    @Autowired private UserRepository userRepository;

    private static final UUID SUPPLIER_FLOUR_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
    private static final UUID RAW_FLOUR_ID = UUID.fromString("55555555-0000-0000-0000-000000000001");
    private static final UUID RAW_OIL_ID = UUID.fromString("55555555-0000-0000-0000-000000000002");
    private static final UUID RAW_SPICE_ID = UUID.fromString("55555555-0000-0000-0000-000000000003");
    private static final UUID PKG_POUCH_ID = UUID.fromString("66666666-0000-0000-0000-000000000001");
    private static final UUID PKG_CARTON_ID = UUID.fromString("66666666-0000-0000-0000-000000000002");
    private static final UUID UNIT_KG_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_MURUKKU_ID = UUID.fromString("77777777-0000-0000-0000-000000000001");

    @Test
    void phase4BenchmarkPurchaseAndProduction() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User supervisor = userRepository.findByUsername("supervisor").orElseThrow();

        // Step 1: Purchase 60 kg flour @ ₹45 = ₹2,700 taxable
        PurchaseDto purchase = purchaseService.createPurchase(new CreatePurchaseRequest(
                SUPPLIER_FLOUR_ID,
                LocalDate.of(2026, 11, 25),
                "PUR-TEST-001",
                "Benchmark flour purchase",
                List.of(new CreatePurchaseItemRequest(
                        "RAW_MATERIAL", RAW_FLOUR_ID, null, UNIT_KG_ID,
                        new BigDecimal("60"), new BigDecimal("45"), BigDecimal.ZERO))
        ), admin);
        purchaseService.receivePurchase(purchase.id(), admin);

        InventoryBalance flourAfterPurchase = inventoryService.getBalance("RAW_MATERIAL", RAW_FLOUR_ID, null, null).orElseThrow();
        assertEquals(0, new BigDecimal("160").compareTo(flourAfterPurchase.getCurrentQuantity()));
        assertEquals(0, new BigDecimal("43.125").compareTo(flourAfterPurchase.getWeightedAvgCost()));

        // Step 2: Create production run of Murukku 200g (1 batch, expected 200 packets)
        ProductionRunDto run = productionService.createRun(new CreateProductionRunRequest(
                PRODUCT_MURUKKU_ID, null, 1, LocalDate.of(2026, 11, 25), "PRD-TEST-001", "Benchmark run"
        ), supervisor);
        assertEquals(200, run.plannedOutput());
        assertEquals(5, run.materialIssues().size());

        // Step 3: Record actuals: flour 40, oil 10.5, spice 1.2, pouches 188 good + 6 damaged, cartons 9
        List<ActualMaterialEntry> actualEntries = run.materialIssues().stream().map(mi -> {
            if (mi.itemName().contains("Flour")) {
                return new ActualMaterialEntry(mi.id(), new BigDecimal("40"), BigDecimal.ZERO);
            } else if (mi.itemName().contains("Oil")) {
                return new ActualMaterialEntry(mi.id(), new BigDecimal("10.5"), BigDecimal.ZERO);
            } else if (mi.itemName().contains("Spice")) {
                return new ActualMaterialEntry(mi.id(), new BigDecimal("1.2"), BigDecimal.ZERO);
            } else if (mi.itemName().contains("Pouch")) {
                return new ActualMaterialEntry(mi.id(), new BigDecimal("188"), new BigDecimal("6"));
            } else {
                return new ActualMaterialEntry(mi.id(), new BigDecimal("9"), BigDecimal.ZERO);
            }
        }).toList();

        productionService.updateActuals(run.id(), new UpdateActualsRequest(188, 6, actualEntries, "Benchmark actuals"), supervisor);
        ProductionRunDto posted = productionService.postRun(run.id(), supervisor);

        assertEquals("POSTED", posted.status());
        assertEquals(188, posted.actualGoodOutput());

        // Assert variances
        assertEquals(0, new BigDecimal("-6.0000").compareTo(posted.yieldVariancePercent()));
        assertTrue(posted.yieldFlagged());

        var oilLine = posted.materialIssues().stream().filter(i -> i.itemName().contains("Oil")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("5.0000").compareTo(oilLine.variancePercent()));
        assertTrue(oilLine.varianceFlagged());

        var pouchLine = posted.materialIssues().stream().filter(i -> i.itemName().contains("Pouch")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("-3.0000").compareTo(pouchLine.variancePercent()));
        assertFalse(pouchLine.varianceFlagged(), "Exactly 3% must NOT be flagged");
        assertEquals(0, new BigDecimal("485.00").compareTo(pouchLine.totalCost()));

        var cartonLine = posted.materialIssues().stream().filter(i -> i.itemName().contains("Carton")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("-10.0000").compareTo(cartonLine.variancePercent()));
        assertTrue(cartonLine.varianceFlagged());

        var spiceLine = posted.materialIssues().stream().filter(i -> i.itemName().contains("Spice")).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(spiceLine.variancePercent()));
        assertFalse(spiceLine.varianceFlagged());

        // Assert closing stock balances
        InventoryBalance flourClosing = inventoryService.getBalance("RAW_MATERIAL", RAW_FLOUR_ID, null, null).orElseThrow();
        assertEquals(0, new BigDecimal("120").compareTo(flourClosing.getCurrentQuantity()));
        assertEquals(0, new BigDecimal("5175.00").compareTo(flourClosing.getTotalValue()));

        assertEquals(0, new BigDecimal("19.5").compareTo(inventoryService.getCurrentStock("RAW_MATERIAL", RAW_OIL_ID, null, null)));
        assertEquals(0, new BigDecimal("3.8").compareTo(inventoryService.getCurrentStock("RAW_MATERIAL", RAW_SPICE_ID, null, null)));
        assertEquals(0, new BigDecimal("306").compareTo(inventoryService.getCurrentStock("PACKAGING", null, PKG_POUCH_ID, null)));
        assertEquals(0, new BigDecimal("21").compareTo(inventoryService.getCurrentStock("PACKAGING", null, PKG_CARTON_ID, null)));
        assertEquals(0, new BigDecimal("238").compareTo(inventoryService.getCurrentStock("FINISHED_GOODS", null, null, PRODUCT_MURUKKU_ID)));

        // Verify pouch ledger has separate PRODUCTION_ISSUE (188) and DAMAGED_MATERIAL (6) movements
        List<StockMovement> pouchLedger = inventoryService.getLedgerForPackaging(PKG_POUCH_ID);
        assertTrue(pouchLedger.stream().anyMatch(m -> "PRODUCTION_ISSUE".equals(m.getMovementType()) && m.getQtyOut().compareTo(new BigDecimal("188")) == 0));
        assertTrue(pouchLedger.stream().anyMatch(m -> "DAMAGED_MATERIAL".equals(m.getMovementType()) && m.getQtyOut().compareTo(new BigDecimal("6")) == 0));

        // Step 8: Add Labour ₹1,200 and Gas/Power ₹600 AFTER posting
        productionService.addOverheadCost(run.id(), new AddCostRequest("LABOUR", "Shift labour", new BigDecimal("1200")), supervisor);
        ProductionRunDto withOverheads = productionService.addOverheadCost(run.id(), new AddCostRequest("GAS", "Gas and power", new BigDecimal("600")), supervisor);

        assertEquals(0, new BigDecimal("5855.00").compareTo(withOverheads.totalCost()));
        assertTrue(withOverheads.costPerPacket().compareTo(new BigDecimal("31.14")) >= 0
                && withOverheads.costPerPacket().compareTo(new BigDecimal("31.15")) <= 0);

        // Ledger reconciliation
        assertTrue(inventoryService.reconcileAll());
    }
}
