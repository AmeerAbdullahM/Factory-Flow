package com.factoryflow.bom;

import com.factoryflow.materials.PackagingMaterial;
import com.factoryflow.materials.RawMaterial;
import com.factoryflow.materials.Unit;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single ingredient / component line within a Bill of Materials.
 *
 * <p>Exactly one of {@link #rawMaterial} or {@link #packagingMaterial} should be non-null,
 * determined by {@link #itemType}.</p>
 */
@Entity
@Table(name = "bom_items")
public class BomItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The parent BOM header this line belongs to.
     * ManyToOne with insertable/updatable false because the header manages the cascade.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_header_id", nullable = false)
    private BomHeader bomHeader;

    /**
     * Type of item on this line.
     * Allowed values: RAW_MATERIAL, PACKAGING
     */
    @Column(name = "item_type", nullable = false, length = 30)
    private String itemType;

    /** Raw material reference — set when itemType = RAW_MATERIAL */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id", nullable = true)
    private RawMaterial rawMaterial;

    /** Packaging material reference — set when itemType = PACKAGING */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "packaging_material_id", nullable = true)
    private PackagingMaterial packagingMaterial;

    /**
     * Required quantity per batch (as defined in the parent BOM header).
     * DECIMAL to handle fractional quantities accurately.
     */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    /** Unit in which the quantity is expressed */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /**
     * Expected wastage percentage for this ingredient, e.g. 2.00 = 2%.
     * Used during production costing to calculate true consumption.
     */
    @Column(name = "wastage_percent", precision = 6, scale = 2)
    private BigDecimal wastagePercent;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public BomHeader getBomHeader() { return bomHeader; }
    public void setBomHeader(BomHeader bomHeader) { this.bomHeader = bomHeader; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public RawMaterial getRawMaterial() { return rawMaterial; }
    public void setRawMaterial(RawMaterial rawMaterial) { this.rawMaterial = rawMaterial; }
    public PackagingMaterial getPackagingMaterial() { return packagingMaterial; }
    public void setPackagingMaterial(PackagingMaterial packagingMaterial) { this.packagingMaterial = packagingMaterial; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public BigDecimal getWastagePercent() { return wastagePercent; }
    public void setWastagePercent(BigDecimal wastagePercent) { this.wastagePercent = wastagePercent; }
}
