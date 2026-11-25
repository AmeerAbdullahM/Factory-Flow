package com.factoryflow.materials;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link RawMaterial} entities.
 */
@Repository
public interface RawMaterialRepository extends JpaRepository<RawMaterial, UUID> {

    /** Find a raw material by its unique code (case-sensitive). */
    Optional<RawMaterial> findByCode(String code);

    /** Return all currently active raw materials. */
    List<RawMaterial> findByActiveTrue();

    /** Case-insensitive partial name search, used for search-as-you-type dropdowns. */
    List<RawMaterial> findByNameContainingIgnoreCase(String name);
}
