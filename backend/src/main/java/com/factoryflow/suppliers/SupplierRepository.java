package com.factoryflow.suppliers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Supplier} entities.
 */
@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    /** Return all currently active suppliers. */
    List<Supplier> findByActiveTrue();

    /** Case-insensitive partial name search for autocomplete / list UI. */
    List<Supplier> findByNameContainingIgnoreCase(String name);
}
