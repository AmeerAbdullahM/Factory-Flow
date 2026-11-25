package com.factoryflow.production;

import com.factoryflow.materials.PackagingMaterial;
import com.factoryflow.materials.RawMaterial;
import com.factoryflow.materials.Unit;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "production_material_issues")
public class ProductionMaterialIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_run_id", nullable = false)
    private ProductionRun productionRun;

    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType; // RAW_MATERIAL or PACKAGING

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id")
    private RawMaterial rawMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "packaging_material_id")
    private PackagingMaterial packagingMaterial;

    @Column(name = "planned_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal plannedQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal actualQuantity;

    @Column(name = "damaged_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal damagedQuantity = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "unit_cost", precision = 14, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "variance_percent", precision = 8, scale = 4)
    private BigDecimal variancePercent;

    @Column(name = "is_variance_flagged", nullable = false)
    private boolean varianceFlagged = false;

    @Column(name = "issue_movement_id")
    private UUID issueMovementId;

    @Column(name = "damaged_movement_id")
    private UUID damagedMovementId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductionRun getProductionRun() { return productionRun; }
    public void setProductionRun(ProductionRun productionRun) { this.productionRun = productionRun; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public RawMaterial getRawMaterial() { return rawMaterial; }
    public void setRawMaterial(RawMaterial rawMaterial) { this.rawMaterial = rawMaterial; }
    public PackagingMaterial getPackagingMaterial() { return packagingMaterial; }
    public void setPackagingMaterial(PackagingMaterial packagingMaterial) { this.packagingMaterial = packagingMaterial; }
    public BigDecimal getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(BigDecimal plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public BigDecimal getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(BigDecimal actualQuantity) { this.actualQuantity = actualQuantity; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(BigDecimal damagedQuantity) { this.damagedQuantity = damagedQuantity; }
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getVariancePercent() { return variancePercent; }
    public void setVariancePercent(BigDecimal variancePercent) { this.variancePercent = variancePercent; }
    public boolean isVarianceFlagged() { return varianceFlagged; }
    public void setVarianceFlagged(boolean varianceFlagged) { this.varianceFlagged = varianceFlagged; }
    public UUID getIssueMovementId() { return issueMovementId; }
    public void setIssueMovementId(UUID issueMovementId) { this.issueMovementId = issueMovementId; }
    public UUID getDamagedMovementId() { return damagedMovementId; }
    public void setDamagedMovementId(UUID damagedMovementId) { this.damagedMovementId = damagedMovementId; }
}
