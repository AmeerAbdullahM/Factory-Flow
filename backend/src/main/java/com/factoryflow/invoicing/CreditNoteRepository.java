package com.factoryflow.invoicing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {
    List<CreditNote> findByInvoice_Id(UUID invoiceId);

    @Query("SELECT COALESCE(SUM(cn.amount), 0) FROM CreditNote cn WHERE cn.invoice.id = :invoiceId")
    BigDecimal sumByInvoice(@Param("invoiceId") UUID invoiceId);
}
