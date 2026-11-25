package com.factoryflow.customers;

import com.factoryflow.users.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer who buys finished goods from the factory.
 * type: RETAILER | DISTRIBUTOR
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Legal / trade name of the customer */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * Customer classification.
     * Allowed values: RETAILER, DISTRIBUTOR
     */
    @Column(nullable = false, length = 20)
    private String type;

    /** GST Identification Number (15-char alphanumeric) */
    @Column(length = 20)
    private String gstin;

    /** Full billing address line */
    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    /**
     * Two-digit GST state code, e.g. "27" for Maharashtra.
     * Required for IGST vs CGST/SGST determination.
     */
    @Column(name = "state_code", length = 5)
    private String stateCode;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    /** Maximum outstanding credit allowed for this customer (DECIMAL, never float) */
    @Column(name = "credit_limit", precision = 18, scale = 2)
    private BigDecimal creditLimit;

    /** Standard payment terms in days, e.g. 30, 45, 60 */
    @Column(name = "payment_terms_days")
    private int paymentTermsDays;

    /**
     * The sales rep / salesman responsible for this customer account.
     * Nullable — head-office customers may not have a dedicated salesman.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_salesman_id", nullable = true)
    private User assignedSalesman;

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
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public int getPaymentTermsDays() { return paymentTermsDays; }
    public void setPaymentTermsDays(int paymentTermsDays) { this.paymentTermsDays = paymentTermsDays; }
    public User getAssignedSalesman() { return assignedSalesman; }
    public void setAssignedSalesman(User assignedSalesman) { this.assignedSalesman = assignedSalesman; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
