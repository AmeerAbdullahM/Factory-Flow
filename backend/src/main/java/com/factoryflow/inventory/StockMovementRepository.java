package com.factoryflow.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link StockMovement}.
 *
 * <p>All query methods return ledger entries ordered chronologically so that
 * callers can replay the running-balance sequence if needed.</p>
 */
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    // -----------------------------------------------------------------------
    // Per-item ledger queries
    // -----------------------------------------------------------------------

    /** All movements for a specific raw material, oldest first. */
    List<StockMovement> findByItemTypeAndRawMaterial_IdOrderByCreatedAtAsc(String itemType, UUID id);

    /** All movements for a specific packaging material, oldest first. */
    List<StockMovement> findByItemTypeAndPackagingMaterial_IdOrderByCreatedAtAsc(String itemType, UUID id);

    /** All movements for a specific finished product, oldest first. */
    List<StockMovement> findByItemTypeAndProduct_IdOrderByCreatedAtAsc(String itemType, UUID id);

    // -----------------------------------------------------------------------
    // Source-document linkage
    // -----------------------------------------------------------------------

    /**
     * Returns all movements that originated from a given source document
     * (e.g. all lines from a particular purchase order or production run).
     */
    List<StockMovement> findBySourceDocumentTypeAndSourceDocumentId(String type, UUID id);

    // -----------------------------------------------------------------------
    // Date-range reporting
    // -----------------------------------------------------------------------

    /**
     * Returns all movements whose {@code createdAt} falls within [from, to],
     * ordered chronologically for period reporting.
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.createdAt BETWEEN :from AND :to ORDER BY sm.createdAt ASC")
    List<StockMovement> findByDateRange(@Param("from") Instant from, @Param("to") Instant to);

    // -----------------------------------------------------------------------
    // Item-type list (most-recent first for display)
    // -----------------------------------------------------------------------

    /** All movements for an item type, most recent first (used by list views). */
    List<StockMovement> findByItemTypeOrderByCreatedAtDesc(String itemType);
}
