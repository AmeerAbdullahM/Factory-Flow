package com.factoryflow.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link InventoryBalance}.
 *
 * <p>Each item (raw material / packaging material / product) has at most one
 * balance row; the {@code findByItemTypeAnd*} methods enforce that uniqueness
 * contract from the query side.</p>
 */
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {

    /** Fetch the balance for a specific raw material. */
    Optional<InventoryBalance> findByItemTypeAndRawMaterial_Id(String type, UUID id);

    /** Fetch the balance for a specific packaging material. */
    Optional<InventoryBalance> findByItemTypeAndPackagingMaterial_Id(String type, UUID id);

    /** Fetch the balance for a specific finished product. */
    Optional<InventoryBalance> findByItemTypeAndProduct_Id(String type, UUID id);

    /** Fetch all balance rows for a given item type (e.g. all raw-material balances). */
    List<InventoryBalance> findByItemType(String type);
}
