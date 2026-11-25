package com.factoryflow.invoicing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByCustomer_Id(UUID customerId);
    List<Invoice> findByInvoiceDateBetween(LocalDate from, LocalDate to);
    List<Invoice> findByStatus(String status);
    List<Invoice> findBySalesOrder_Id(UUID salesOrderId);
    List<Invoice> findAllByOrderByCreatedAtDesc();

    @Query("SELECT i FROM Invoice i WHERE i.customer.id = :customerId AND i.status = 'ACTIVE'")
    List<Invoice> findActiveByCustomer(@Param("customerId") UUID customerId);
}
