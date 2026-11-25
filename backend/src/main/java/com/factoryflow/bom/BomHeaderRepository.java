package com.factoryflow.bom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link BomHeader} entities.
 */
@Repository
public interface BomHeaderRepository extends JpaRepository<BomHeader, UUID> {

    /**
     * Returns the single active BOM for a product.
     * Business rule: only one BOM version may be active per product at a time.
     */
    Optional<BomHeader> findByProductIdAndActiveTrue(UUID productId);

    /**
     * Look up a specific version of a product's BOM (e.g. for historical costing).
     */
    Optional<BomHeader> findByProductIdAndVersion(UUID productId, int version);

    /**
     * Return all BOM versions ever created for a product, ordered by version ascending.
     */
    List<BomHeader> findByProductId(UUID productId);
}
