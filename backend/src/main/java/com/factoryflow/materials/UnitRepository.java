package com.factoryflow.materials;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Unit} entities.
 */
@Repository
public interface UnitRepository extends JpaRepository<Unit, UUID> {

    /** Find a unit by its unique symbol (e.g. "kg", "ml"). */
    Optional<Unit> findBySymbol(String symbol);

    /**
     * Return all units of a given type.
     * @param unitType one of: WEIGHT, VOLUME, COUNT
     */
    List<Unit> findByUnitType(String unitType);
}
