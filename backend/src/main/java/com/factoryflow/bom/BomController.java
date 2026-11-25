package com.factoryflow.bom;

import com.factoryflow.common.BusinessException;
import com.factoryflow.materials.PackagingMaterialRepository;
import com.factoryflow.materials.RawMaterialRepository;
import com.factoryflow.materials.UnitRepository;
import com.factoryflow.products.Product;
import com.factoryflow.products.ProductRepository;
import com.factoryflow.users.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bom")
public class BomController {

    private final BomHeaderRepository bomRepo;
    private final ProductRepository productRepo;
    private final RawMaterialRepository rawRepo;
    private final PackagingMaterialRepository pkgRepo;
    private final UnitRepository unitRepo;

    public BomController(BomHeaderRepository bomRepo, ProductRepository productRepo,
                         RawMaterialRepository rawRepo, PackagingMaterialRepository pkgRepo,
                         UnitRepository unitRepo) {
        this.bomRepo = bomRepo;
        this.productRepo = productRepo;
        this.rawRepo = rawRepo;
        this.pkgRepo = pkgRepo;
        this.unitRepo = unitRepo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<BomHeaderDto>> listAll() {
        return ResponseEntity.ok(bomRepo.findAll().stream().map(this::toDto).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<BomHeaderDto> get(@PathVariable UUID id) {
        BomHeader bom = bomRepo.findById(id)
                .orElseThrow(() -> new BusinessException("BOM not found: " + id, HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(toDto(bom));
    }

    /**
     * Resolves the active BOM for a product.
     * Supports pack-size reuse: if the product has a baseProduct, falls back to baseProduct's active BOM
     * and scales quantities by bomConversionFactor.
     */
    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<BomHeaderDto> getForProduct(@PathVariable UUID productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new BusinessException("Product not found: " + productId, HttpStatus.NOT_FOUND));

        var directBom = bomRepo.findByProductIdAndActiveTrue(productId);
        if (directBom.isPresent()) {
            return ResponseEntity.ok(toDto(directBom.get()));
        }

        // Pack-size reuse: if product links to a baseProduct, use baseProduct's BOM
        if (product.getBaseProduct() != null) {
            BomHeader baseBom = bomRepo.findByProductIdAndActiveTrue(product.getBaseProduct().getId())
                    .orElseThrow(() -> new BusinessException("No active BOM found for product or base product", HttpStatus.NOT_FOUND));
            BigDecimal factor = product.getBomConversionFactor() != null ? product.getBomConversionFactor() : BigDecimal.ONE;
            return ResponseEntity.ok(toScaledDto(baseBom, product, factor));
        }

        throw new BusinessException("No active BOM found for product: " + product.getName(), HttpStatus.NOT_FOUND);
    }

    @GetMapping("/{id}/planned-consumption")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<PlannedConsumptionItem>> plannedConsumption(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int batches) {
        BomHeader bom = bomRepo.findById(id)
                .orElseThrow(() -> new BusinessException("BOM not found: " + id, HttpStatus.NOT_FOUND));
        BigDecimal mult = BigDecimal.valueOf(batches);
        List<PlannedConsumptionItem> result = bom.getItems().stream().map(item -> {
            String name = "RAW_MATERIAL".equals(item.getItemType())
                    ? item.getRawMaterial().getName()
                    : item.getPackagingMaterial().getName();
            UUID itemId = "RAW_MATERIAL".equals(item.getItemType())
                    ? item.getRawMaterial().getId()
                    : item.getPackagingMaterial().getId();
            return new PlannedConsumptionItem(
                    item.getId(), name, item.getItemType(), itemId,
                    item.getQuantity().multiply(mult), item.getUnit().getSymbol());
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    @Transactional
    public ResponseEntity<BomHeaderDto> create(@Valid @RequestBody CreateBomRequest req,
                                               @AuthenticationPrincipal User user) {
        Product product = productRepo.findById(req.productId())
                .orElseThrow(() -> new BusinessException("Product not found", HttpStatus.NOT_FOUND));

        // Deactivate previous active BOM for this product and bump version
        List<BomHeader> existing = bomRepo.findByProductId(req.productId());
        int nextVersion = existing.stream().mapToInt(BomHeader::getVersion).max().orElse(0) + 1;
        existing.forEach(b -> { b.setActive(false); bomRepo.save(b); });

        BomHeader bom = new BomHeader();
        bom.setCode(req.code());
        bom.setProduct(product);
        bom.setBatchSize(req.batchSize() > 0 ? req.batchSize() : 1);
        bom.setExpectedOutput(req.expectedOutput());
        bom.setVersion(nextVersion);
        bom.setEffectiveDate(req.effectiveDate() != null ? req.effectiveDate() : LocalDate.now());
        bom.setActive(true);
        bom.setNotes(req.notes());
        bom.setCreatedBy(user);

        List<BomItem> items = new ArrayList<>();
        for (CreateBomItemRequest itemReq : req.items()) {
            BomItem bi = new BomItem();
            bi.setBomHeader(bom);
            bi.setItemType(itemReq.itemType());
            if ("RAW_MATERIAL".equals(itemReq.itemType())) {
                bi.setRawMaterial(rawRepo.findById(itemReq.rawMaterialId())
                        .orElseThrow(() -> new BusinessException("Raw material not found", HttpStatus.NOT_FOUND)));
            } else {
                bi.setPackagingMaterial(pkgRepo.findById(itemReq.packagingMaterialId())
                        .orElseThrow(() -> new BusinessException("Packaging material not found", HttpStatus.NOT_FOUND)));
            }
            bi.setQuantity(itemReq.quantity());
            bi.setUnit(unitRepo.findById(itemReq.unitId())
                    .orElseThrow(() -> new BusinessException("Unit not found", HttpStatus.NOT_FOUND)));
            bi.setWastagePercent(itemReq.wastagePercent() != null ? itemReq.wastagePercent() : BigDecimal.ZERO);
            items.add(bi);
        }
        bom.setItems(items);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(bomRepo.save(bom)));
    }

    private BomHeaderDto toDto(BomHeader b) {
        List<BomItemDto> itemDtos = b.getItems().stream().map(i -> new BomItemDto(
                i.getId(),
                i.getItemType(),
                i.getRawMaterial() != null ? i.getRawMaterial().getId() : null,
                i.getRawMaterial() != null ? i.getRawMaterial().getName() : null,
                i.getPackagingMaterial() != null ? i.getPackagingMaterial().getId() : null,
                i.getPackagingMaterial() != null ? i.getPackagingMaterial().getName() : null,
                i.getQuantity(),
                i.getUnit().getSymbol(),
                i.getUnit().getId(),
                i.getWastagePercent()
        )).toList();

        return new BomHeaderDto(b.getId(), b.getCode(), b.getProduct().getId(), b.getProduct().getName(),
                b.getBatchSize(), b.getExpectedOutput(), b.getVersion(), b.getEffectiveDate(),
                b.isActive(), b.getNotes(), itemDtos);
    }

    private BomHeaderDto toScaledDto(BomHeader base, Product targetProduct, BigDecimal factor) {
        List<BomItemDto> itemDtos = base.getItems().stream().map(i -> {
            // Raw materials scale by conversion factor; packaging stays per-packet/carton
            BigDecimal qty = "RAW_MATERIAL".equals(i.getItemType())
                    ? i.getQuantity().multiply(factor)
                    : i.getQuantity();
            return new BomItemDto(
                    i.getId(),
                    i.getItemType(),
                    i.getRawMaterial() != null ? i.getRawMaterial().getId() : null,
                    i.getRawMaterial() != null ? i.getRawMaterial().getName() : null,
                    i.getPackagingMaterial() != null ? i.getPackagingMaterial().getId() : null,
                    i.getPackagingMaterial() != null ? i.getPackagingMaterial().getName() : null,
                    qty,
                    i.getUnit().getSymbol(),
                    i.getUnit().getId(),
                    i.getWastagePercent()
            );
        }).toList();

        return new BomHeaderDto(base.getId(), base.getCode() + " (Scaled for " + targetProduct.getCode() + ")",
                targetProduct.getId(), targetProduct.getName(),
                base.getBatchSize(), base.getExpectedOutput(), base.getVersion(), base.getEffectiveDate(),
                base.isActive(), base.getNotes(), itemDtos);
    }

    public record BomHeaderDto(UUID id, String code, UUID productId, String productName,
                               int batchSize, int expectedOutput, int version,
                               LocalDate effectiveDate, boolean active, String notes, List<BomItemDto> items) {}

    public record BomItemDto(UUID id, String itemType, UUID rawMaterialId, String rawMaterialName,
                             UUID packagingMaterialId, String packagingMaterialName,
                             BigDecimal quantity, String unitSymbol, UUID unitId, BigDecimal wastagePercent) {}

    public record PlannedConsumptionItem(UUID bomItemId, String itemName, String itemType, UUID itemId,
                                         BigDecimal plannedQuantity, String unitSymbol) {}

    public record CreateBomRequest(@NotBlank String code, @NotNull UUID productId, int batchSize,
                                   int expectedOutput, LocalDate effectiveDate, String notes,
                                   @NotEmpty List<CreateBomItemRequest> items) {}

    public record CreateBomItemRequest(@NotBlank String itemType, UUID rawMaterialId, UUID packagingMaterialId,
                                       @NotNull BigDecimal quantity, @NotNull UUID unitId, BigDecimal wastagePercent) {}
}
