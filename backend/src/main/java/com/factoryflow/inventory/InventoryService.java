package com.factoryflow.inventory;

import com.factoryflow.common.BusinessException;
import com.factoryflow.common.WeightedAverageCostCalculator;
import com.factoryflow.materials.PackagingMaterial;
import com.factoryflow.materials.RawMaterial;
import com.factoryflow.products.Product;
import com.factoryflow.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core inventory ledger service.
 *
 * The stock ledger (stock_movements) is the source of truth.
 * inventory_balances is a cache updated atomically in each transaction.
 *
 * Key invariants:
 * 1. Every movement has a source document reference
 * 2. No negative stock (enforced before each deduction)
 * 3. Weighted average cost updated on every receipt
 * 4. Balance cache always reconciles with ledger sum
 */
@Service
public class InventoryService {

    private final StockMovementRepository movementRepo;
    private final InventoryBalanceRepository balanceRepo;

    public InventoryService(StockMovementRepository movementRepo,
                            InventoryBalanceRepository balanceRepo) {
        this.movementRepo = movementRepo;
        this.balanceRepo = balanceRepo;
    }

    // =========================================================
    // RAW MATERIAL OPERATIONS
    // =========================================================

    @Transactional
    public StockMovement receiveRawMaterial(RawMaterial material, BigDecimal qty,
                                            BigDecimal unitCost, String sourceType,
                                            UUID sourceId, String reference, User user) {
        InventoryBalance balance = getOrCreateRawBalance(material);

        BigDecimal newAvgCost = WeightedAverageCostCalculator.computeNewAverage(
                balance.getCurrentQuantity(), balance.getWeightedAvgCost(), qty, unitCost);

        BigDecimal newQty = balance.getCurrentQuantity().add(qty);
        balance.setCurrentQuantity(newQty);
        balance.setWeightedAvgCost(newAvgCost);
        balance.setTotalValue(WeightedAverageCostCalculator.closingValue(newQty, newAvgCost));
        balance.setLastUpdated(Instant.now());
        balanceRepo.save(balance);

        StockMovement movement = new StockMovement();
        movement.setItemType("RAW_MATERIAL");
        movement.setRawMaterial(material);
        movement.setMovementType("PURCHASE_RECEIPT");
        movement.setQtyIn(qty);
        movement.setRunningBalance(newQty);
        movement.setUnitCost(unitCost);
        movement.setTotalValue(WeightedAverageCostCalculator.computeValue(qty, unitCost));
        movement.setSourceDocumentType(sourceType);
        movement.setSourceDocumentId(sourceId);
        movement.setReferenceNumber(reference);
        movement.setCreatedBy(user);
        return movementRepo.save(movement);
    }

    @Transactional
    public StockMovement issueRawMaterial(RawMaterial material, BigDecimal qty,
                                          String movementType, String sourceType,
                                          UUID sourceId, String reference, User user) {
        InventoryBalance balance = getOrCreateRawBalance(material);
        validateSufficientStock(balance.getCurrentQuantity(), qty, material.getName());

        BigDecimal newQty = balance.getCurrentQuantity().subtract(qty);
        BigDecimal unitCost = balance.getWeightedAvgCost();

        balance.setCurrentQuantity(newQty);
        balance.setTotalValue(WeightedAverageCostCalculator.closingValue(newQty, unitCost));
        balance.setLastUpdated(Instant.now());
        balanceRepo.save(balance);

        StockMovement movement = new StockMovement();
        movement.setItemType("RAW_MATERIAL");
        movement.setRawMaterial(material);
        movement.setMovementType(movementType);
        movement.setQtyOut(qty);
        movement.setRunningBalance(newQty);
        movement.setUnitCost(unitCost);
        movement.setTotalValue(WeightedAverageCostCalculator.computeValue(qty, unitCost));
        movement.setSourceDocumentType(sourceType);
        movement.setSourceDocumentId(sourceId);
        movement.setReferenceNumber(reference);
        movement.setCreatedBy(user);
        return movementRepo.save(movement);
    }

    // =========================================================
    // PACKAGING MATERIAL OPERATIONS
    // =========================================================

    @Transactional
    public StockMovement receivePackagingMaterial(PackagingMaterial material, BigDecimal qty,
                                                  BigDecimal unitCost, String sourceType,
                                                  UUID sourceId, String reference, User user) {
        InventoryBalance balance = getOrCreatePkgBalance(material);

        BigDecimal newAvgCost = WeightedAverageCostCalculator.computeNewAverage(
                balance.getCurrentQuantity(), balance.getWeightedAvgCost(), qty, unitCost);

        BigDecimal newQty = balance.getCurrentQuantity().add(qty);
        balance.setCurrentQuantity(newQty);
        balance.setWeightedAvgCost(newAvgCost);
        balance.setTotalValue(WeightedAverageCostCalculator.closingValue(newQty, newAvgCost));
        balance.setLastUpdated(Instant.now());
        balanceRepo.save(balance);

        StockMovement movement = new StockMovement();
        movement.setItemType("PACKAGING");
        movement.setPackagingMaterial(material);
        movement.setMovementType("PURCHASE_RECEIPT");
        movement.setQtyIn(qty);
        movement.setRunningBalance(newQty);
        movement.setUnitCost(unitCost);
        movement.setTotalValue(WeightedAverageCostCalculator.computeValue(qty, unitCost));
        movement.setSourceDocumentType(sourceType);
        movement.setSourceDocumentId(sourceId);
        movement.setReferenceNumber(reference);
        movement.setCreatedBy(user);
        return movementRepo.save(movement);
    }

    @Transactional
    public StockMovement issuePackagingMaterial(PackagingMaterial material, BigDecimal qty,
                                                String movementType, String sourceType,
                                                UUID sourceId, String reference, User user) {
        InventoryBalance balance = getOrCreatePkgBalance(material);
        validateSufficientStock(balance.getCurrentQuantity(), qty, material.getName());

        BigDecimal newQty = balance.getCurrentQuantity().subtract(qty);
        BigDecimal unitCost = balance.getWeightedAvgCost();

        balance.setCurrentQuantity(newQty);
        balance.setTotalValue(WeightedAverageCostCalculator.closingValue(newQty, unitCost));
        balance.setLastUpdated(Instant.now());
        balanceRepo.save(balance);

        StockMovement movement = new StockMovement();
        movement.setItemType("PACKAGING");
        movement.setPackagingMaterial(material);
        movement.setMovementType(movementType);
        movement.setQtyOut(qty);
        movement.setRunningBalance(newQty);
        movement.setUnitCost(unitCost);
        movement.setTotalValue(WeightedAverageCostCalculator.computeValue(qty, unitCost));
        movement.setSourceDocumentType(sourceType);
        movement.setSourceDocumentId(sourceId);
        movement.setReferenceNumber(reference);
        movement.setCreatedBy(user);
        return movementRepo.save(movement);
    }

    // =========================================================
    // FINISHED GOODS OPERATIONS
    // =========================================================

    @Transactional
    public StockMovement addFinishedGoods(Product product, BigDecimal qty,
                                          BigDecimal costPerPacket, String sourceType,
                                          UUID sourceId, String reference, User user) {
        InventoryBalance balance = getOrCreateFgBalance(product);

        BigDecimal newAvgCost = WeightedAverageCostCalculator.computeNewAverage(
                balance.getCurrentQuantity(), balance.getWeightedAvgCost(), qty, costPerPacket);

        BigDecimal newQty = balance.getCurrentQuantity().add(qty);
        balance.setCurrentQuantity(newQty);
        balance.setWeightedAvgCost(newAvgCost);
        balance.setTotalValue(WeightedAverageCostCalculator.closingValue(newQty, newAvgCost));
        balance.setLastUpdated(Instant.now());
        balanceRepo.save(balance);

        StockMovement movement = new StockMovement();
        movement.setItemType("FINISHED_GOODS");
        movement.setProduct(product);
        movement.setMovementType("PRODUCTION_OUTPUT");
        movement.setQtyIn(qty);
        movement.setRunningBalance(newQty);
        movement.setUnitCost(costPerPacket);
        movement.setTotalValue(WeightedAverageCostCalculator.computeValue(qty, costPerPacket));
        movement.setSourceDocumentType(sourceType);
        movement.setSourceDocumentId(sourceId);
        movement.setReferenceNumber(reference);
        movement.setCreatedBy(user);
        return movementRepo.save(movement);
    }

    @Transactional
    public StockMovement dispatchFinishedGoods(Product product, BigDecimal qty,
                                               String sourceType, UUID sourceId,
                                               String reference, User user) {
        InventoryBalance balance = getOrCreateFgBalance(product);
        validateSufficientStock(balance.getCurrentQuantity(), qty, product.getName());

        BigDecimal newQty = balance.getCurrentQuantity().subtract(qty);
        BigDecimal unitCost = balance.getWeightedAvgCost();

        balance.setCurrentQuantity(newQty);
        balance.setTotalValue(WeightedAverageCostCalculator.closingValue(newQty, unitCost));
        balance.setLastUpdated(Instant.now());
        balanceRepo.save(balance);

        StockMovement movement = new StockMovement();
        movement.setItemType("FINISHED_GOODS");
        movement.setProduct(product);
        movement.setMovementType("SALES_DISPATCH");
        movement.setQtyOut(qty);
        movement.setRunningBalance(newQty);
        movement.setUnitCost(unitCost);
        movement.setTotalValue(WeightedAverageCostCalculator.computeValue(qty, unitCost));
        movement.setSourceDocumentType(sourceType);
        movement.setSourceDocumentId(sourceId);
        movement.setReferenceNumber(reference);
        movement.setCreatedBy(user);
        return movementRepo.save(movement);
    }

    // =========================================================
    // QUERY & RECONCILIATION METHODS
    // =========================================================

    public Optional<InventoryBalance> getBalance(String itemType, UUID rawMatId,
                                                   UUID pkgMatId, UUID productId) {
        if ("RAW_MATERIAL".equals(itemType)) {
            return balanceRepo.findByItemTypeAndRawMaterial_Id(itemType, rawMatId);
        } else if ("PACKAGING".equals(itemType)) {
            return balanceRepo.findByItemTypeAndPackagingMaterial_Id(itemType, pkgMatId);
        } else {
            return balanceRepo.findByItemTypeAndProduct_Id(itemType, productId);
        }
    }

    public BigDecimal getCurrentStock(String itemType, UUID rawMatId, UUID pkgMatId, UUID productId) {
        return getBalance(itemType, rawMatId, pkgMatId, productId)
                .map(InventoryBalance::getCurrentQuantity)
                .orElse(BigDecimal.ZERO);
    }

    public List<InventoryBalance> getAllBalances() {
        return balanceRepo.findAll();
    }

    public List<InventoryBalance> getBalancesByType(String type) {
        return balanceRepo.findByItemType(type);
    }

    public List<StockMovement> getLedgerForRawMaterial(UUID id) {
        return movementRepo.findByItemTypeAndRawMaterial_IdOrderByCreatedAtAsc("RAW_MATERIAL", id);
    }

    public List<StockMovement> getLedgerForPackaging(UUID id) {
        return movementRepo.findByItemTypeAndPackagingMaterial_IdOrderByCreatedAtAsc("PACKAGING", id);
    }

    public List<StockMovement> getLedgerForProduct(UUID id) {
        return movementRepo.findByItemTypeAndProduct_IdOrderByCreatedAtAsc("FINISHED_GOODS", id);
    }

    public List<StockMovement> getAllMovements() {
        return movementRepo.findAll();
    }

    /**
     * Reconciles each balance row against the sum of its ledger movements (qtyIn - qtyOut).
     * Returns true if all balances match the ledger.
     */
    public boolean reconcileAll() {
        List<InventoryBalance> balances = balanceRepo.findAll();
        for (InventoryBalance b : balances) {
            List<StockMovement> movements;
            if ("RAW_MATERIAL".equals(b.getItemType()) && b.getRawMaterial() != null) {
                movements = getLedgerForRawMaterial(b.getRawMaterial().getId());
            } else if ("PACKAGING".equals(b.getItemType()) && b.getPackagingMaterial() != null) {
                movements = getLedgerForPackaging(b.getPackagingMaterial().getId());
            } else if ("FINISHED_GOODS".equals(b.getItemType()) && b.getProduct() != null) {
                movements = getLedgerForProduct(b.getProduct().getId());
            } else {
                continue;
            }
            BigDecimal ledgerSum = movements.stream()
                    .map(m -> (m.getQtyIn() != null ? m.getQtyIn() : BigDecimal.ZERO)
                            .subtract(m.getQtyOut() != null ? m.getQtyOut() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (ledgerSum.compareTo(b.getCurrentQuantity()) != 0) {
                return false;
            }
        }
        return true;
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private InventoryBalance getOrCreateRawBalance(RawMaterial material) {
        return balanceRepo.findByItemTypeAndRawMaterial_Id("RAW_MATERIAL", material.getId())
                .orElseGet(() -> {
                    InventoryBalance b = new InventoryBalance();
                    b.setItemType("RAW_MATERIAL");
                    b.setRawMaterial(material);
                    b.setLastUpdated(Instant.now());
                    return b;
                });
    }

    private InventoryBalance getOrCreatePkgBalance(PackagingMaterial material) {
        return balanceRepo.findByItemTypeAndPackagingMaterial_Id("PACKAGING", material.getId())
                .orElseGet(() -> {
                    InventoryBalance b = new InventoryBalance();
                    b.setItemType("PACKAGING");
                    b.setPackagingMaterial(material);
                    b.setLastUpdated(Instant.now());
                    return b;
                });
    }

    private InventoryBalance getOrCreateFgBalance(Product product) {
        return balanceRepo.findByItemTypeAndProduct_Id("FINISHED_GOODS", product.getId())
                .orElseGet(() -> {
                    InventoryBalance b = new InventoryBalance();
                    b.setItemType("FINISHED_GOODS");
                    b.setProduct(product);
                    b.setLastUpdated(Instant.now());
                    return b;
                });
    }

    private void validateSufficientStock(BigDecimal available, BigDecimal required, String itemName) {
        if (available.compareTo(required) < 0) {
            throw new BusinessException(
                    "Insufficient stock for " + itemName,
                    "Available: " + available + ", Required: " + required,
                    HttpStatus.CONFLICT);
        }
    }
}
