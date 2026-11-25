package com.factoryflow.purchases;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Purchase} entities.
 */
@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    /** All purchases from a given supplier (most recent first by DB ordering). */
    List<Purchase> findBySupplierId(UUID supplierId);

    /** Filter by workflow status (DRAFT, CONFIRMED, RECEIVED, CANCELLED). */
    List<Purchase> findByStatus(String status);

    /**
     * Date-range query — useful for monthly GRN reports.
     * Both bounds are inclusive.
     */
    List<Purchase> findByPurchaseDateBetween(LocalDate from, LocalDate to);

    /** Look up a purchase by its human-readable reference number. */
    Optional<Purchase> findByReferenceNumber(String referenceNumber);
}
