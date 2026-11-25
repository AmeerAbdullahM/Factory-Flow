package com.factoryflow.inventory;

import com.factoryflow.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/summary")
    public ResponseEntity<List<InventoryBalanceDto>> summary(@RequestParam(required = false) String itemType) {
        List<InventoryBalance> balances = itemType != null && !itemType.isBlank()
                ? inventoryService.getBalancesByType(itemType)
                : inventoryService.getAllBalances();
        return ResponseEntity.ok(balances.stream().map(this::toBalanceDto).toList());
    }

    @GetMapping("/summary/raw")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<InventoryBalanceDto>> rawSummary() {
        return ResponseEntity.ok(inventoryService.getBalancesByType("RAW_MATERIAL").stream().map(this::toBalanceDto).toList());
    }

    @GetMapping("/summary/packaging")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<InventoryBalanceDto>> pkgSummary() {
        return ResponseEntity.ok(inventoryService.getBalancesByType("PACKAGING").stream().map(this::toBalanceDto).toList());
    }

    @GetMapping("/summary/fg")
    public ResponseEntity<List<InventoryBalanceDto>> fgSummary() {
        return ResponseEntity.ok(inventoryService.getBalancesByType("FINISHED_GOODS").stream().map(this::toBalanceDto).toList());
    }

    @GetMapping("/fg/{productId}")
    public ResponseEntity<InventoryBalanceDto> fgBalance(@PathVariable UUID productId) {
        InventoryBalance b = inventoryService.getBalance("FINISHED_GOODS", null, null, productId)
                .orElseThrow(() -> new BusinessException("No inventory record for product: " + productId, HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(toBalanceDto(b));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<StockMovementDto>> ledger(
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) UUID itemId) {
        List<StockMovement> movements;
        if (itemType != null && itemId != null) {
            movements = switch (itemType) {
                case "RAW_MATERIAL" -> inventoryService.getLedgerForRawMaterial(itemId);
                case "PACKAGING" -> inventoryService.getLedgerForPackaging(itemId);
                case "FINISHED_GOODS" -> inventoryService.getLedgerForProduct(itemId);
                default -> throw new BusinessException("Invalid itemType: " + itemType);
            };
        } else {
            movements = inventoryService.getAllMovements().stream()
                    .filter(m -> itemType == null || itemType.equals(m.getItemType()))
                    .sorted(Comparator.comparing(StockMovement::getCreatedAt).reversed())
                    .toList();
        }
        return ResponseEntity.ok(movements.stream().map(this::toMovementDto).toList());
    }

    @GetMapping("/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER','ACCOUNTANT')")
    public ResponseEntity<ReconciliationResult> reconcile() {
        boolean ok = inventoryService.reconcileAll();
        return ResponseEntity.ok(new ReconciliationResult(ok, ok ? "All balances match stock ledger" : "Discrepancy detected"));
    }

    private InventoryBalanceDto toBalanceDto(InventoryBalance b) {
        UUID itemId;
        String name;
        String code;
        String unitSymbol;
        BigDecimal reorderLevel = BigDecimal.ZERO;

        if ("RAW_MATERIAL".equals(b.getItemType()) && b.getRawMaterial() != null) {
            itemId = b.getRawMaterial().getId();
            name = b.getRawMaterial().getName();
            code = b.getRawMaterial().getCode();
            unitSymbol = b.getRawMaterial().getUnit().getSymbol();
            reorderLevel = b.getRawMaterial().getReorderLevel();
        } else if ("PACKAGING".equals(b.getItemType()) && b.getPackagingMaterial() != null) {
            itemId = b.getPackagingMaterial().getId();
            name = b.getPackagingMaterial().getName();
            code = b.getPackagingMaterial().getCode();
            unitSymbol = b.getPackagingMaterial().getUnit().getSymbol();
            reorderLevel = b.getPackagingMaterial().getReorderLevel();
        } else if (b.getProduct() != null) {
            itemId = b.getProduct().getId();
            name = b.getProduct().getName();
            code = b.getProduct().getCode();
            unitSymbol = b.getProduct().getUnit() != null ? b.getProduct().getUnit().getSymbol() : "pkt";
        } else {
            itemId = null;
            name = "Unknown";
            code = "";
            unitSymbol = "";
        }

        boolean lowStock = reorderLevel != null && reorderLevel.compareTo(BigDecimal.ZERO) > 0
                && b.getCurrentQuantity().compareTo(reorderLevel) <= 0;

        return new InventoryBalanceDto(
                b.getId(), itemId, b.getItemType(), name, code,
                b.getCurrentQuantity(), unitSymbol, b.getWeightedAvgCost(),
                b.getTotalValue(), reorderLevel, lowStock
        );
    }

    private StockMovementDto toMovementDto(StockMovement m) {
        String name;
        if ("RAW_MATERIAL".equals(m.getItemType()) && m.getRawMaterial() != null) {
            name = m.getRawMaterial().getName();
        } else if ("PACKAGING".equals(m.getItemType()) && m.getPackagingMaterial() != null) {
            name = m.getPackagingMaterial().getName();
        } else if (m.getProduct() != null) {
            name = m.getProduct().getName();
        } else {
            name = "Unknown";
        }
        String username = m.getCreatedBy() != null ? m.getCreatedBy().getUsername() : "system";
        return new StockMovementDto(
                m.getId(), m.getMovementDate(), m.getItemType(), name,
                m.getMovementType(), m.getQtyIn(), m.getQtyOut(),
                m.getRunningBalance(), m.getUnitCost(), m.getTotalValue(),
                m.getSourceDocumentType(), m.getSourceDocumentId(),
                m.getReferenceNumber(), username, m.getNotes()
        );
    }

    public record InventoryBalanceDto(UUID id, UUID itemId, String itemType, String itemName, String itemCode,
                                      BigDecimal currentQuantity, String unitSymbol, BigDecimal weightedAvgCost,
                                      BigDecimal totalValue, BigDecimal reorderLevel, boolean lowStock) {}

    public record StockMovementDto(UUID id, Instant movementDate, String itemType, String itemName,
                                   String movementType, BigDecimal qtyIn, BigDecimal qtyOut,
                                   BigDecimal runningBalance, BigDecimal unitCost, BigDecimal totalValue,
                                   String sourceDocumentType, UUID sourceDocumentId,
                                   String referenceNumber, String createdBy, String notes) {}

    public record ReconciliationResult(boolean reconciled, String message) {}
}
