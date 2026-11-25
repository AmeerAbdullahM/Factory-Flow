package com.factoryflow.products;

import com.factoryflow.materials.Unit;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A finished good / SKU sold by the factory.
 *
 * <p>Products can form a hierarchy: a "base product" (e.g. plain biscuit dough)
 * may have multiple pack-size variants linked via {@link #baseProduct}.
 * {@link #bomConversionFactor} records how many base-product units are needed
 * to produce 1 unit of this variant.</p>
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Display name of the product / SKU */
    @Column(nullable = false, length = 200)
    private String name;

    /** Unique product code / SKU code, e.g. "PRD-001" */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Product category, e.g. "Biscuits", "Namkeen" */
    @Column(length = 100)
    private String category;

    /** Net weight of one pack in grams (DECIMAL to handle fractional grams) */
    @Column(name = "pack_size_grams", precision = 18, scale = 4)
    private BigDecimal packSizeGrams;

    /** Human-readable pack size label, e.g. "500g", "1 kg" */
    @Column(name = "pack_size_description", length = 50)
    private String packSizeDescription;

    /**
     * Optional parent / base product for pack-size variant relationships.
     * E.g. "Cream Biscuit 200g" and "Cream Biscuit 500g" both point to the same base.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_product_id", nullable = true)
    private Product baseProduct;

    /**
     * When a BOM is defined on the base product, this factor scales the BOM quantities
     * to produce one unit of THIS variant. Defaults to 1.0 (no scaling).
     */
    @Column(name = "bom_conversion_factor", nullable = false, precision = 18, scale = 6)
    private BigDecimal bomConversionFactor = BigDecimal.ONE;

    /** Selling price (MRP or ex-factory) per unit, stored as DECIMAL */
    @Column(name = "selling_price", precision = 18, scale = 4)
    private BigDecimal sellingPrice;

    /** Applicable GST rate as a percentage, e.g. 12.00 for 12% */
    @Column(name = "gst_rate", precision = 6, scale = 2)
    private BigDecimal gstRate;

    /** HSN code for GST classification, e.g. "1905" */
    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    /** Unit in which this product is sold/stocked (e.g. pcs, carton) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = true)
    private Unit unit;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPackSizeGrams() { return packSizeGrams; }
    public void setPackSizeGrams(BigDecimal packSizeGrams) { this.packSizeGrams = packSizeGrams; }
    public String getPackSizeDescription() { return packSizeDescription; }
    public void setPackSizeDescription(String packSizeDescription) { this.packSizeDescription = packSizeDescription; }
    public Product getBaseProduct() { return baseProduct; }
    public void setBaseProduct(Product baseProduct) { this.baseProduct = baseProduct; }
    public BigDecimal getBomConversionFactor() { return bomConversionFactor; }
    public void setBomConversionFactor(BigDecimal bomConversionFactor) { this.bomConversionFactor = bomConversionFactor; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
    public BigDecimal getGstRate() { return gstRate; }
    public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }
    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
