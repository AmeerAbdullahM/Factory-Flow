package com.factoryflow.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
    @Query("SELECT COALESCE(SUM(pa.allocatedAmount), 0) FROM PaymentAllocation pa WHERE pa.invoice.id = :invoiceId")
    BigDecimal sumAllocatedForInvoice(@Param("invoiceId") UUID invoiceId);

    List<PaymentAllocation> findByInvoice_Id(UUID invoiceId);
    List<PaymentAllocation> findByPayment_Id(UUID paymentId);
}
