package com.factoryflow.production;

import com.factoryflow.bom.BomHeader;
import com.factoryflow.products.Product;
import com.factoryflow.users.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "production_runs")
public class ProductionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_number", nullable = false, unique = true, length = 100)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_header_id", nullable = false)
    private BomHeader bomHeader;

    @Column(name = "bom_version", nullable = false)
    private int bomVersion;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "planned_batches", nullable = false)
    private int plannedBatches = 1;

    @Column(name = "planned_output", nullable = false)
    private int plannedOutput;

    @Column(name = "actual_good_output")
    private Integer actualGoodOutput;

    @Column(name = "wastage_packets", nullable = false)
    private int wastagePackets = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "total_material_cost", precision = 12, scale = 2)
    private BigDecimal totalMaterialCost;

    @Column(name = "total_packaging_cost", precision = 12, scale = 2)
    private BigDecimal totalPackagingCost;

    @Column(name = "total_labour_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLabourCost = BigDecimal.ZERO;

    @Column(name = "total_overhead_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalOverheadCost = BigDecimal.ZERO;

    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "cost_per_packet", precision = 10, scale = 4)
    private BigDecimal costPerPacket;

    @Column(name = "yield_variance_percent", precision = 8, scale = 4)
    private BigDecimal yieldVariancePercent;

    @Column(name = "is_yield_flagged", nullable = false)
    private boolean yieldFlagged = false;

    @OneToMany(mappedBy = "productionRun", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<ProductionMaterialIssue> materialIssues = new ArrayList<>();

    @OneToMany(mappedBy = "productionRun", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<ProductionCost> costs = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by")
    private User postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public BomHeader getBomHeader() { return bomHeader; }
    public void setBomHeader(BomHeader bomHeader) { this.bomHeader = bomHeader; }
    public int getBomVersion() { return bomVersion; }
    public void setBomVersion(int bomVersion) { this.bomVersion = bomVersion; }
    public LocalDate getRunDate() { return runDate; }
    public void setRunDate(LocalDate runDate) { this.runDate = runDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPlannedBatches() { return plannedBatches; }
    public void setPlannedBatches(int plannedBatches) { this.plannedBatches = plannedBatches; }
    public int getPlannedOutput() { return plannedOutput; }
    public void setPlannedOutput(int plannedOutput) { this.plannedOutput = plannedOutput; }
    public Integer getActualGoodOutput() { return actualGoodOutput; }
    public void setActualGoodOutput(Integer actualGoodOutput) { this.actualGoodOutput = actualGoodOutput; }
    public int getWastagePackets() { return wastagePackets; }
    public void setWastagePackets(int wastagePackets) { this.wastagePackets = wastagePackets; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getTotalMaterialCost() { return totalMaterialCost; }
    public void setTotalMaterialCost(BigDecimal totalMaterialCost) { this.totalMaterialCost = totalMaterialCost; }
    public BigDecimal getTotalPackagingCost() { return totalPackagingCost; }
    public void setTotalPackagingCost(BigDecimal totalPackagingCost) { this.totalPackagingCost = totalPackagingCost; }
    public BigDecimal getTotalLabourCost() { return totalLabourCost; }
    public void setTotalLabourCost(BigDecimal totalLabourCost) { this.totalLabourCost = totalLabourCost; }
    public BigDecimal getTotalOverheadCost() { return totalOverheadCost; }
    public void setTotalOverheadCost(BigDecimal totalOverheadCost) { this.totalOverheadCost = totalOverheadCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getCostPerPacket() { return costPerPacket; }
    public void setCostPerPacket(BigDecimal costPerPacket) { this.costPerPacket = costPerPacket; }
    public BigDecimal getYieldVariancePercent() { return yieldVariancePercent; }
    public void setYieldVariancePercent(BigDecimal yieldVariancePercent) { this.yieldVariancePercent = yieldVariancePercent; }
    public boolean isYieldFlagged() { return yieldFlagged; }
    public void setYieldFlagged(boolean yieldFlagged) { this.yieldFlagged = yieldFlagged; }
    public List<ProductionMaterialIssue> getMaterialIssues() { return materialIssues; }
    public void setMaterialIssues(List<ProductionMaterialIssue> materialIssues) { this.materialIssues = materialIssues; }
    public List<ProductionCost> getCosts() { return costs; }
    public void setCosts(List<ProductionCost> costs) { this.costs = costs; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public User getPostedBy() { return postedBy; }
    public void setPostedBy(User postedBy) { this.postedBy = postedBy; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
