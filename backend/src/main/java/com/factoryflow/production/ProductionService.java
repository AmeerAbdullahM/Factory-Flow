package com.factoryflow.production;

import com.factoryflow.audit.AuditService;
import com.factoryflow.bom.BomHeader;
import com.factoryflow.bom.BomHeaderRepository;
import com.factoryflow.bom.BomItem;
import com.factoryflow.common.BusinessException;
import com.factoryflow.common.UnitConverter;
import com.factoryflow.common.WeightedAverageCostCalculator;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.inventory.StockMovement;
import com.factoryflow.products.Product;
import com.factoryflow.products.ProductRepository;
import com.factoryflow.users.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
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
public class ProductionService {

    private final ProductionRunRepository runRepo;
    private final BomHeaderRepository bomRepo;
    private final ProductRepository productRepo;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final ProductionCostRepository costRepo;

    @Value("${app.variance.tolerance-percent:3.0}")
    private BigDecimal tolerancePercent = new BigDecimal("3.0");

    public ProductionService(ProductionRunRepository runRepo,
                             BomHeaderRepository bomRepo,
                             ProductRepository productRepo,
                             InventoryService inventoryService,
                             AuditService auditService,
                             ProductionCostRepository costRepo) {
        this.runRepo = runRepo;
        this.bomRepo = bomRepo;
        this.productRepo = productRepo;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
        this.costRepo = costRepo;
    }

    public List<ProductionRunDto> getAll() {
        return runRepo.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    public ProductionRunDto getById(UUID id) {
        return toDto(getEntity(id));
    }

    public List<ProductionRunDto> getByProduct(UUID productId) {
        return runRepo.findByProduct_Id(productId).stream().map(this::toDto).toList();
    }

    private ProductionRun getEntity(UUID id) {
        return runRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Production run not found: " + id, HttpStatus.NOT_FOUND));
    }

    /**
     * Create a production run in DRAFT status.
     * Calculates planned material consumption from the active (or specified) BOM.
     * Supports pack-size reuse when a product links to a baseProduct.
     */
    @Transactional
    public ProductionRunDto createRun(CreateProductionRunRequest req, User user) {
        Product product = productRepo.findById(req.productId())
                .orElseThrow(() -> new BusinessException("Product not found", HttpStatus.NOT_FOUND));

        BomHeader bom;
        BigDecimal scaleFactor = BigDecimal.ONE;
        if (req.bomHeaderId() != null) {
            bom = bomRepo.findById(req.bomHeaderId())
                    .orElseThrow(() -> new BusinessException("BOM not found", HttpStatus.NOT_FOUND));
        } else {
            var direct = bomRepo.findByProductIdAndActiveTrue(product.getId());
            if (direct.isPresent()) {
                bom = direct.get();
            } else if (product.getBaseProduct() != null) {
                bom = bomRepo.findByProductIdAndActiveTrue(product.getBaseProduct().getId())
                        .orElseThrow(() -> new BusinessException("No active BOM for product or base product", HttpStatus.NOT_FOUND));
                scaleFactor = product.getBomConversionFactor() != null ? product.getBomConversionFactor() : BigDecimal.ONE;
            } else {
                throw new BusinessException("No active BOM found for product: " + product.getName(), HttpStatus.NOT_FOUND);
            }
        }

        int batches = req.plannedBatches() > 0 ? req.plannedBatches() : 1;
        BigDecimal batchMult = BigDecimal.valueOf(batches);

        ProductionRun run = new ProductionRun();
        run.setReferenceNumber(req.referenceNumber() != null && !req.referenceNumber().isBlank()
                ? req.referenceNumber()
                : "PRD-" + System.currentTimeMillis());
        run.setProduct(product);
        run.setBomHeader(bom);
        run.setBomVersion(bom.getVersion());
        run.setRunDate(req.runDate() != null ? req.runDate() : LocalDate.now());
        run.setStatus("DRAFT");
        run.setPlannedBatches(batches);
        run.setPlannedOutput(bom.getExpectedOutput() * batches);
        run.setNotes(req.notes());
        run.setCreatedBy(user);

        List<ProductionMaterialIssue> issues = new ArrayList<>();
        for (BomItem bi : bom.getItems()) {
            ProductionMaterialIssue issue = new ProductionMaterialIssue();
            issue.setProductionRun(run);
            issue.setItemType(bi.getItemType());
            issue.setRawMaterial(bi.getRawMaterial());
            issue.setPackagingMaterial(bi.getPackagingMaterial());
            issue.setUnit(bi.getUnit());

            BigDecimal planned = bi.getQuantity().multiply(batchMult);
            if ("RAW_MATERIAL".equals(bi.getItemType())) {
                planned = planned.multiply(scaleFactor);
            }
            planned = planned.setScale(4, RoundingMode.HALF_UP);

            issue.setPlannedQuantity(planned);
            issue.setActualQuantity(planned); // default actual = planned until supervisor edits
            issue.setDamagedQuantity(BigDecimal.ZERO);
            issues.add(issue);
        }
        run.setMaterialIssues(issues);

        ProductionRun saved = runRepo.save(run);
        auditService.log(user, "PRODUCTION_RUN_CREATED", "PRODUCTION_RUN", saved.getId(),
                null, Map.of("reference", saved.getReferenceNumber(), "plannedOutput", saved.getPlannedOutput()),
                saved.getReferenceNumber());

        return toDto(saved);
    }

    /**
     * Update actual quantities, damaged packaging, and good output before posting (while in DRAFT).
     */
    @Transactional
    public ProductionRunDto updateActuals(UUID runId, UpdateActualsRequest req, User user) {
        ProductionRun run = getEntity(runId);
        if (!"DRAFT".equals(run.getStatus())) {
            throw new BusinessException("Cannot edit actuals on a " + run.getStatus() + " production run",
                    HttpStatus.CONFLICT);
        }

        run.setActualGoodOutput(req.actualGoodOutput());
        run.setWastagePackets(req.wastagePackets());
        if (req.notes() != null) {
            run.setNotes(req.notes());
        }

        for (ActualMaterialEntry entry : req.materials()) {
            ProductionMaterialIssue issue = run.getMaterialIssues().stream()
                    .filter(i -> i.getId().equals(entry.materialIssueId())
                            || (i.getRawMaterial() != null && i.getRawMaterial().getId().equals(entry.materialIssueId()))
                            || (i.getPackagingMaterial() != null && i.getPackagingMaterial().getId().equals(entry.materialIssueId())))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Material issue line not found: " + entry.materialIssueId(),
                            HttpStatus.NOT_FOUND));

            issue.setActualQuantity(entry.actualQuantity());
            issue.setDamagedQuantity(entry.damagedQuantity() != null ? entry.damagedQuantity() : BigDecimal.ZERO);
        }

        ProductionRun saved = runRepo.save(run);
        return toDto(saved);
    }

    /**
     * POST A PRODUCTION RUN in ONE atomic transaction.
     *
     * 1. Deduct actual raw materials (PRODUCTION_ISSUE)
     * 2. Deduct actual packaging used (PRODUCTION_ISSUE) and damaged packaging separately (DAMAGED_MATERIAL)
     * 3. Compute material variance & yield variance; flag ONLY when |variance%| > tolerance (strictly greater)
     * 4. Compute material + packaging costs at current weighted-average cost
     * 5. Add actualGoodOutput packets to FG stock (PRODUCTION_OUTPUT)
     * 6. Save run as POSTED and write audit log
     */
    @Transactional
    public ProductionRunDto postRun(UUID runId, User user) {
        ProductionRun run = getEntity(runId);
        if (!"DRAFT".equals(run.getStatus())) {
            throw new BusinessException("Only DRAFT runs can be posted. Current status: " + run.getStatus(),
                    HttpStatus.CONFLICT);
        }
        if (run.getActualGoodOutput() == null || run.getActualGoodOutput() <= 0) {
            throw new BusinessException("Actual good output must be greater than zero before posting");
        }

        BigDecimal totalRawCost = BigDecimal.ZERO;
        BigDecimal totalPkgCost = BigDecimal.ZERO;

        for (ProductionMaterialIssue issue : run.getMaterialIssues()) {
            BigDecimal actualGood = issue.getActualQuantity();
            BigDecimal damaged = issue.getDamagedQuantity() != null ? issue.getDamagedQuantity() : BigDecimal.ZERO;
            BigDecimal totalConsumed = actualGood.add(damaged);

            if ("RAW_MATERIAL".equals(issue.getItemType())) {
                BigDecimal qtyInMaterialUnit = UnitConverter.convert(
                        actualGood, issue.getUnit().getSymbol(), issue.getRawMaterial().getUnit().getSymbol());

                StockMovement sm = inventoryService.issueRawMaterial(
                        issue.getRawMaterial(), qtyInMaterialUnit,
                        "PRODUCTION_ISSUE", "PRODUCTION_RUN", run.getId(), run.getReferenceNumber(), user);

                issue.setIssueMovementId(sm.getId());
                issue.setUnitCost(sm.getUnitCost());
                BigDecimal lineCost = WeightedAverageCostCalculator.computeValue(qtyInMaterialUnit, sm.getUnitCost());
                issue.setTotalCost(lineCost);
                totalRawCost = totalRawCost.add(lineCost);

            } else {
                // PACKAGING:
                // 1) Issue good packaging used (e.g. 188 pouches or 9 cartons)
                BigDecimal goodInPkgUnit = UnitConverter.convert(
                        actualGood, issue.getUnit().getSymbol(), issue.getPackagingMaterial().getUnit().getSymbol());
                StockMovement smGood = inventoryService.issuePackagingMaterial(
                        issue.getPackagingMaterial(), goodInPkgUnit,
                        "PRODUCTION_ISSUE", "PRODUCTION_RUN", run.getId(), run.getReferenceNumber(), user);
                issue.setIssueMovementId(smGood.getId());

                // 2) Issue damaged packaging as a separate DAMAGED_MATERIAL movement (e.g. 6 damaged pouches)
                BigDecimal damagedInPkgUnit = BigDecimal.ZERO;
                if (damaged.compareTo(BigDecimal.ZERO) > 0) {
                    damagedInPkgUnit = UnitConverter.convert(
                            damaged, issue.getUnit().getSymbol(), issue.getPackagingMaterial().getUnit().getSymbol());
                    StockMovement smDmg = inventoryService.issuePackagingMaterial(
                            issue.getPackagingMaterial(), damagedInPkgUnit,
                            "DAMAGED_MATERIAL", "PRODUCTION_RUN", run.getId(), run.getReferenceNumber(), user);
                    issue.setDamagedMovementId(smDmg.getId());
                }

                // Total packaging cost = (good + damaged) * weightedAvgCost
                BigDecimal totalPkgQty = goodInPkgUnit.add(damagedInPkgUnit);
                issue.setUnitCost(smGood.getUnitCost());
                BigDecimal lineCost = WeightedAverageCostCalculator.computeValue(totalPkgQty, smGood.getUnitCost());
                issue.setTotalCost(lineCost);
                totalPkgCost = totalPkgCost.add(lineCost);
            }

            // Variance uses totalConsumed (for pouches: 188 + 6 = 194 vs 200 = -3.0%, not flagged!)
            BigDecimal varPct = UnitConverter.variancePercent(totalConsumed, issue.getPlannedQuantity());
            issue.setVariancePercent(varPct);
            issue.setVarianceFlagged(UnitConverter.isFlagged(varPct, tolerancePercent));
        }

        // Yield variance
        BigDecimal yieldVarPct = UnitConverter.variancePercent(
                BigDecimal.valueOf(run.getActualGoodOutput()),
                BigDecimal.valueOf(run.getPlannedOutput()));
        run.setYieldVariancePercent(yieldVarPct);
        run.setYieldFlagged(UnitConverter.isFlagged(yieldVarPct, tolerancePercent));

        // Costing totals
        run.setTotalMaterialCost(totalRawCost.setScale(2, RoundingMode.HALF_UP));
        run.setTotalPackagingCost(totalPkgCost.setScale(2, RoundingMode.HALF_UP));
        recalculateCosts(run);

        // Add actual good packets to FG stock at current cost per packet
        inventoryService.addFinishedGoods(
                run.getProduct(),
                BigDecimal.valueOf(run.getActualGoodOutput()),
                run.getCostPerPacket(),
                "PRODUCTION_RUN",
                run.getId(),
                run.getReferenceNumber(),
                user);

        run.setStatus("POSTED");
        run.setPostedBy(user);
        run.setPostedAt(Instant.now());
        ProductionRun saved = runRepo.save(run);

        auditService.log(user, "PRODUCTION_RUN_POSTED", "PRODUCTION_RUN", saved.getId(),
                Map.of("status", "DRAFT"),
                Map.of("status", "POSTED", "actualGoodOutput", saved.getActualGoodOutput(),
                        "totalCost", saved.getTotalCost(), "costPerPacket", saved.getCostPerPacket()),
                saved.getReferenceNumber());

        return toDto(saved);
    }

    /**
     * Add or append overhead/labour costs on a run (supported AFTER posting).
     * Recalculates total cost and cost per packet, and audit-logs the change.
     */
    @Transactional
    public ProductionRunDto addOverheadCost(UUID runId, AddCostRequest req, User user) {
        ProductionRun run = getEntity(runId);
        if ("CANCELLED".equals(run.getStatus())) {
            throw new BusinessException("Cannot add costs to a cancelled run", HttpStatus.CONFLICT);
        }

        BigDecimal oldTotal = run.getTotalCost() != null ? run.getTotalCost() : BigDecimal.ZERO;
        BigDecimal oldCostPerPacket = run.getCostPerPacket() != null ? run.getCostPerPacket() : BigDecimal.ZERO;

        ProductionCost cost = new ProductionCost();
        cost.setProductionRun(run);
        cost.setCostType(req.costType().toUpperCase());
        cost.setDescription(req.description());
        cost.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        cost.setAddedBy(user);
        run.getCosts().add(cost);

        recalculateCosts(run);
        ProductionRun saved = runRepo.save(run);

        auditService.log(user, "PRODUCTION_COST_ADDED", "PRODUCTION_RUN", saved.getId(),
                Map.of("totalCost", oldTotal, "costPerPacket", oldCostPerPacket),
                Map.of("costType", cost.getCostType(), "amount", cost.getAmount(),
                        "totalCost", saved.getTotalCost(), "costPerPacket", saved.getCostPerPacket()),
                saved.getReferenceNumber());

        return toDto(saved);
    }

    private void recalculateCosts(ProductionRun run) {
        BigDecimal matCost = run.getTotalMaterialCost() != null ? run.getTotalMaterialCost() : BigDecimal.ZERO;
        BigDecimal pkgCost = run.getTotalPackagingCost() != null ? run.getTotalPackagingCost() : BigDecimal.ZERO;

        BigDecimal labour = run.getCosts().stream()
                .filter(c -> "LABOUR".equalsIgnoreCase(c.getCostType()))
                .map(ProductionCost::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overhead = run.getCosts().stream()
                .filter(c -> !"LABOUR".equalsIgnoreCase(c.getCostType()))
                .map(ProductionCost::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        run.setTotalLabourCost(labour.setScale(2, RoundingMode.HALF_UP));
        run.setTotalOverheadCost(overhead.setScale(2, RoundingMode.HALF_UP));

        BigDecimal total = matCost.add(pkgCost).add(labour).add(overhead).setScale(2, RoundingMode.HALF_UP);
        run.setTotalCost(total);

        if (run.getActualGoodOutput() != null && run.getActualGoodOutput() > 0) {
            run.setCostPerPacket(total.divide(BigDecimal.valueOf(run.getActualGoodOutput()), 4, RoundingMode.HALF_UP));
        }
    }

    public ProductionRunDto toDto(ProductionRun r) {
        List<MaterialIssueDto> issueDtos = r.getMaterialIssues().stream().map(i -> {
            String name = "RAW_MATERIAL".equals(i.getItemType())
                    ? i.getRawMaterial().getName()
                    : i.getPackagingMaterial().getName();
            return new MaterialIssueDto(
                    i.getId(), name, i.getItemType(),
                    i.getPlannedQuantity(), i.getActualQuantity(), i.getDamagedQuantity(),
                    i.getUnit().getSymbol(), i.getVariancePercent(), i.isVarianceFlagged(),
                    i.getUnitCost(), i.getTotalCost()
            );
        }).toList();

        List<CostDto> costDtos = r.getCosts().stream().map(c -> new CostDto(
                c.getId(), c.getCostType(), c.getDescription(), c.getAmount(), c.getCreatedAt()
        )).toList();

        BigDecimal sellingPrice = r.getProduct().getSellingPrice();
        BigDecimal marginPerPacket = null;
        BigDecimal marginPercent = null;
        if (r.getCostPerPacket() != null && sellingPrice != null && sellingPrice.compareTo(BigDecimal.ZERO) > 0) {
            marginPerPacket = sellingPrice.subtract(r.getCostPerPacket()).setScale(2, RoundingMode.HALF_UP);
            marginPercent = marginPerPacket.divide(sellingPrice, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        }

        return new ProductionRunDto(
                r.getId(), r.getReferenceNumber(), r.getProduct().getId(), r.getProduct().getName(),
                r.getBomHeader().getId(), r.getBomVersion(), r.getPlannedBatches(), r.getPlannedOutput(),
                r.getActualGoodOutput(), r.getWastagePackets(), r.getStatus(), r.getRunDate(), r.getNotes(),
                r.getTotalMaterialCost(), r.getTotalPackagingCost(), r.getTotalLabourCost(),
                r.getTotalOverheadCost(), r.getTotalCost(), r.getCostPerPacket(),
                sellingPrice, marginPerPacket, marginPercent,
                r.getYieldVariancePercent(), r.isYieldFlagged(), issueDtos, costDtos
        );
    }

    public record ProductionRunDto(
            UUID id, String referenceNumber, UUID productId, String productName,
            UUID bomHeaderId, int bomVersion, int plannedBatches, int plannedOutput,
            Integer actualGoodOutput, int wastagePackets, String status, LocalDate runDate, String notes,
            BigDecimal totalMaterialCost, BigDecimal totalPackagingCost, BigDecimal totalLabourCost,
            BigDecimal totalOverheadCost, BigDecimal totalCost, BigDecimal costPerPacket,
            BigDecimal sellingPrice, BigDecimal marginPerPacket, BigDecimal marginPercent,
            BigDecimal yieldVariancePercent, boolean yieldFlagged,
            List<MaterialIssueDto> materialIssues, List<CostDto> costs) {}

    public record MaterialIssueDto(
            UUID id, String itemName, String itemType, BigDecimal plannedQuantity,
            BigDecimal actualQuantity, BigDecimal damagedQuantity, String unitSymbol,
            BigDecimal variancePercent, boolean varianceFlagged, BigDecimal unitCost, BigDecimal totalCost) {}

    public record CostDto(UUID id, String costType, String description, BigDecimal amount, Instant createdAt) {}

    public record CreateProductionRunRequest(
            @NotNull UUID productId, UUID bomHeaderId, int plannedBatches,
            LocalDate runDate, String referenceNumber, String notes) {}

    public record UpdateActualsRequest(
            @NotNull Integer actualGoodOutput, int wastagePackets,
            @NotEmpty List<ActualMaterialEntry> materials, String notes) {}

    public record ActualMaterialEntry(
            @NotNull UUID materialIssueId, @NotNull BigDecimal actualQuantity, BigDecimal damagedQuantity) {}

    public record AddCostRequest(
            @NotBlank String costType, String description, @NotNull BigDecimal amount) {}
}
