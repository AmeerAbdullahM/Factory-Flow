package com.factoryflow.purchases;

import com.factoryflow.audit.AuditService;
import com.factoryflow.common.BusinessException;
import com.factoryflow.common.UnitConverter;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.materials.PackagingMaterial;
import com.factoryflow.materials.PackagingMaterialRepository;
import com.factoryflow.materials.RawMaterial;
import com.factoryflow.materials.RawMaterialRepository;
import com.factoryflow.materials.Unit;
import com.factoryflow.materials.UnitRepository;
import com.factoryflow.suppliers.Supplier;
import com.factoryflow.suppliers.SupplierRepository;
import com.factoryflow.users.User;
import jakarta.validation.constraints.NotBlank;
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
public class PurchaseService {

    private final PurchaseRepository purchaseRepo;
    private final SupplierRepository supplierRepo;
    private final RawMaterialRepository rawRepo;
    private final PackagingMaterialRepository pkgRepo;
    private final UnitRepository unitRepo;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public PurchaseService(PurchaseRepository purchaseRepo, SupplierRepository supplierRepo,
                           RawMaterialRepository rawRepo, PackagingMaterialRepository pkgRepo,
                           UnitRepository unitRepo, InventoryService inventoryService,
                           AuditService auditService) {
        this.purchaseRepo = purchaseRepo;
        this.supplierRepo = supplierRepo;
        this.rawRepo = rawRepo;
        this.pkgRepo = pkgRepo;
        this.unitRepo = unitRepo;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    public List<PurchaseDto> getAll() {
        return purchaseRepo.findAll().stream().map(this::toDto).toList();
    }

    public PurchaseDto getById(UUID id) {
        return toDto(purchaseRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Purchase not found: " + id, HttpStatus.NOT_FOUND)));
    }

    @Transactional
    public PurchaseDto createPurchase(CreatePurchaseRequest req, User user) {
        Supplier supplier = supplierRepo.findById(req.supplierId())
                .orElseThrow(() -> new BusinessException("Supplier not found", HttpStatus.NOT_FOUND));

        Purchase purchase = new Purchase();
        purchase.setReferenceNumber(req.referenceNumber() != null && !req.referenceNumber().isBlank()
                ? req.referenceNumber()
                : "PUR-" + System.currentTimeMillis());
        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(req.purchaseDate());
        purchase.setStatus("DRAFT");
        purchase.setNotes(req.notes());
        purchase.setCreatedBy(user);

        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalGst = BigDecimal.ZERO;
        List<PurchaseItem> items = new ArrayList<>();

        for (CreatePurchaseItemRequest itemReq : req.items()) {
            Unit unit = unitRepo.findById(itemReq.unitId())
                    .orElseThrow(() -> new BusinessException("Unit not found", HttpStatus.NOT_FOUND));

            PurchaseItem pi = new PurchaseItem();
            pi.setPurchase(purchase);
            pi.setItemType(itemReq.itemType());

            if ("RAW_MATERIAL".equals(itemReq.itemType())) {
                RawMaterial rm = rawRepo.findById(itemReq.rawMaterialId())
                        .orElseThrow(() -> new BusinessException("Raw material not found", HttpStatus.NOT_FOUND));
                pi.setRawMaterial(rm);
            } else if ("PACKAGING".equals(itemReq.itemType())) {
                PackagingMaterial pm = pkgRepo.findById(itemReq.packagingMaterialId())
                        .orElseThrow(() -> new BusinessException("Packaging material not found", HttpStatus.NOT_FOUND));
                pi.setPackagingMaterial(pm);
            } else {
                throw new BusinessException("Invalid itemType: " + itemReq.itemType());
            }

            pi.setUnit(unit);
            pi.setQuantity(itemReq.quantity());
            pi.setUnitRate(itemReq.unitRate());

            BigDecimal taxable = itemReq.quantity().multiply(itemReq.unitRate()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal gstRate = itemReq.gstRate() != null ? itemReq.gstRate() : BigDecimal.ZERO;
            BigDecimal gstAmt = taxable.multiply(gstRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            pi.setTaxableAmount(taxable);
            pi.setGstRate(gstRate);
            pi.setGstAmount(gstAmt);
            pi.setTotalAmount(taxable.add(gstAmt));

            totalTaxable = totalTaxable.add(taxable);
            totalGst = totalGst.add(gstAmt);
            items.add(pi);
        }

        purchase.setItems(items);
        purchase.setTaxableAmount(totalTaxable);
        purchase.setGstAmount(totalGst);
        purchase.setTotalAmount(totalTaxable.add(totalGst));

        Purchase saved = purchaseRepo.save(purchase);
        auditService.log(user, "PURCHASE_CREATED", "PURCHASE", saved.getId(),
                null, Map.of("reference", saved.getReferenceNumber(), "total", saved.getTotalAmount()),
                saved.getReferenceNumber());

        return toDto(saved);
    }

    /**
     * Confirm receipt of a purchase in ONE transaction.
     * Increases stock, writes ledger movements, and updates weighted average cost.
     */
    @Transactional
    public PurchaseDto receivePurchase(UUID purchaseId, User user) {
        Purchase purchase = purchaseRepo.findById(purchaseId)
                .orElseThrow(() -> new BusinessException("Purchase not found: " + purchaseId, HttpStatus.NOT_FOUND));

        if (!"DRAFT".equals(purchase.getStatus())) {
            throw new BusinessException("Only DRAFT purchases can be received. Current status: " + purchase.getStatus(),
                    HttpStatus.CONFLICT);
        }

        for (PurchaseItem item : purchase.getItems()) {
            if ("RAW_MATERIAL".equals(item.getItemType())) {
                RawMaterial rm = item.getRawMaterial();
                BigDecimal qtyInMaterialUnit = UnitConverter.convert(
                        item.getQuantity(), item.getUnit().getSymbol(), rm.getUnit().getSymbol());
                BigDecimal ratePerMaterialUnit = item.getTaxableAmount()
                        .divide(qtyInMaterialUnit, 6, RoundingMode.HALF_UP);

                inventoryService.receiveRawMaterial(
                        rm, qtyInMaterialUnit, ratePerMaterialUnit,
                        "PURCHASE", purchase.getId(), purchase.getReferenceNumber(), user);
            } else {
                PackagingMaterial pm = item.getPackagingMaterial();
                BigDecimal qtyInMaterialUnit = UnitConverter.convert(
                        item.getQuantity(), item.getUnit().getSymbol(), pm.getUnit().getSymbol());
                BigDecimal ratePerMaterialUnit = item.getTaxableAmount()
                        .divide(qtyInMaterialUnit, 6, RoundingMode.HALF_UP);

                inventoryService.receivePackagingMaterial(
                        pm, qtyInMaterialUnit, ratePerMaterialUnit,
                        "PURCHASE", purchase.getId(), purchase.getReferenceNumber(), user);
            }
        }

        purchase.setStatus("RECEIVED");
        purchase.setReceivedBy(user);
        purchase.setReceivedAt(Instant.now());
        Purchase saved = purchaseRepo.save(purchase);

        auditService.log(user, "PURCHASE_RECEIVED", "PURCHASE", saved.getId(),
                Map.of("status", "DRAFT"), Map.of("status", "RECEIVED"), saved.getReferenceNumber());

        return toDto(saved);
    }

    public PurchaseDto toDto(Purchase p) {
        List<PurchaseItemDto> itemDtos = p.getItems().stream().map(i -> new PurchaseItemDto(
                i.getId(),
                i.getItemType(),
                i.getRawMaterial() != null ? i.getRawMaterial().getId() : null,
                i.getRawMaterial() != null ? i.getRawMaterial().getName() : null,
                i.getPackagingMaterial() != null ? i.getPackagingMaterial().getId() : null,
                i.getPackagingMaterial() != null ? i.getPackagingMaterial().getName() : null,
                i.getQuantity(),
                i.getUnit().getSymbol(),
                i.getUnitRate(),
                i.getTaxableAmount(),
                i.getGstRate(),
                i.getGstAmount(),
                i.getTotalAmount()
        )).toList();

        return new PurchaseDto(
                p.getId(), p.getReferenceNumber(), p.getSupplier().getId(), p.getSupplier().getName(),
                p.getPurchaseDate(), p.getStatus(), p.getTaxableAmount(), p.getGstAmount(),
                p.getTotalAmount(), p.getNotes(), itemDtos
        );
    }

    public record PurchaseDto(UUID id, String referenceNumber, UUID supplierId, String supplierName,
                              LocalDate purchaseDate, String status, BigDecimal taxableAmount,
                              BigDecimal gstAmount, BigDecimal totalAmount, String notes,
                              List<PurchaseItemDto> items) {}

    public record PurchaseItemDto(UUID id, String itemType, UUID rawMaterialId, String rawMaterialName,
                                  UUID packagingMaterialId, String packagingMaterialName,
                                  BigDecimal quantity, String unitSymbol, BigDecimal unitRate,
                                  BigDecimal taxableAmount, BigDecimal gstRate, BigDecimal gstAmount,
                                  BigDecimal totalAmount) {}

    public record CreatePurchaseRequest(@NotNull UUID supplierId, @NotNull LocalDate purchaseDate,
                                        String referenceNumber, String notes,
                                        @NotEmpty List<CreatePurchaseItemRequest> items) {}

    public record CreatePurchaseItemRequest(@NotBlank String itemType, UUID rawMaterialId,
                                            UUID packagingMaterialId, @NotNull UUID unitId,
                                            @NotNull BigDecimal quantity, @NotNull BigDecimal unitRate,
                                            BigDecimal gstRate) {}
}
