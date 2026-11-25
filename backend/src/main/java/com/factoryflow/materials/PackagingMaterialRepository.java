package com.factoryflow.materials;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PackagingMaterial} entities.
 */
@Repository
public interface PackagingMaterialRepository extends JpaRepository<PackagingMaterial, UUID> {

    /** Find a packaging material by its unique code (case-sensitive). */
    Optional<PackagingMaterial> findByCode(String code);

    /** Return all currently active packaging materials. */
    List<PackagingMaterial> findByActiveTrue();
}
