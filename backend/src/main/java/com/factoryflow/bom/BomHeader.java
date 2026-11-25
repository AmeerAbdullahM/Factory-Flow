package com.factoryflow.bom;

import com.factoryflow.products.Product;
import com.factoryflow.users.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bill of Materials header — defines the recipe for producing a {@link Product}.
 *
 * <p>Multiple versions can exist for the same product; only the one with
 * {@code active = true} is used for production orders and costing.</p>
 */
@Entity
@Table(name = "bom_headers")
public class BomHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique BOM code, e.g. "BOM-001-V2" */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** The finished good this BOM produces */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Standard batch size (number of units, e.g. number of pouches per batch).
     * BOM ingredient quantities are expressed for this batch size.
     */
    @Column(name = "batch_size", nullable = false)
    private int batchSize;

    /**
     * Expected output units after wastage, i.e. saleable units per batch.
     * expectedOutput <= batchSize.
     */
    @Column(name = "expected_output", nullable = false)
    private int expectedOutput;

    /** Version counter — start at 1, increment when the recipe changes */
    @Column(nullable = false)
    private int version = 1;

    /**
     * Date from which this BOM version is valid.
     * Used for cost roll-ups on historical production orders.
     */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /**
     * Only one BOM version per product should be active at a time.
     * Deactivate the old version before activating a new one.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** User who created / approved this BOM version */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = true)
    private User createdBy;

    /**
     * Ingredient lines for this BOM.
     * Fetched EAGER so the full recipe is always available without an extra query.
     * CascadeType.ALL ensures items are persisted/deleted with the header.
     */
    @OneToMany(mappedBy = "bomHeader", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<BomItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(int expectedOutput) { this.expectedOutput = expectedOutput; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public List<BomItem> getItems() { return items; }
    public void setItems(List<BomItem> items) { this.items = items; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
