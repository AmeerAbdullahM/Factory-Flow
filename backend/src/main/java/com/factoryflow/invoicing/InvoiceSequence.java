package com.factoryflow.invoicing;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "invoice_sequences")
public class InvoiceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financial_year", nullable = false, unique = true, length = 10)
    private String financialYear;

    @Column(name = "last_sequence", nullable = false)
    private int lastSequence = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }
    public int getLastSequence() { return lastSequence; }
    public void setLastSequence(int lastSequence) { this.lastSequence = lastSequence; }
}
