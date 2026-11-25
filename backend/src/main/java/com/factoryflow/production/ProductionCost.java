package com.factoryflow.production;

import com.factoryflow.users.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "production_costs")
public class ProductionCost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_run_id", nullable = false)
    private ProductionRun productionRun;

    @Column(name = "cost_type", nullable = false, length = 50)
    private String costType; // LABOUR, GAS, POWER, OTHER

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductionRun getProductionRun() { return productionRun; }
    public void setProductionRun(ProductionRun productionRun) { this.productionRun = productionRun; }
    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public User getAddedBy() { return addedBy; }
    public void setAddedBy(User addedBy) { this.addedBy = addedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
