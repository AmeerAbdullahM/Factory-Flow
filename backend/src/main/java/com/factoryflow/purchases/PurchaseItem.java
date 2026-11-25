package com.factoryflow.purchases;

import com.factoryflow.materials.PackagingMaterial;
import com.factoryflow.materials.RawMaterial;
import com.factoryflow.materials.Unit;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item in a purchase order / GRN.
 *
 * <p>Exactly one of {@link #rawMaterial} or {@link #packagingMaterial} will be non-null,
 * determined by {@link #itemType}.</p>
 */
@Entity
@Table(name = "purchase_items")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Parent purchase order */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    /**
     * Type of item being purchased.
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

    /** Quantity received/ordered, DECIMAL for fractional support */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    /** Unit of measure for the quantity */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /** Price per unit before tax */
    @Column(name = "unit_rate", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitRate;

    /** quantity × unitRate (pre-tax line total) */
    @Column(name = "taxable_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxableAmount;

    /** GST rate percentage applied to this line, e.g. 18.00 for 18% */
    @Column(name = "gst_rate", nullable = false, precision = 6, scale = 2)
    private BigDecimal gstRate;

    /** GST rupee amount for this line */
    @Column(name = "gst_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal gstAmount;

    /** taxableAmount + gstAmount */
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Purchase getPurchase() { return purchase; }
    public void setPurchase(Purchase purchase) { this.purchase = purchase; }
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
    public BigDecimal getUnitRate() { return unitRate; }
    public void setUnitRate(BigDecimal unitRate) { this.unitRate = unitRate; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }
    public BigDecimal getGstRate() { return gstRate; }
    public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }
    public BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}
