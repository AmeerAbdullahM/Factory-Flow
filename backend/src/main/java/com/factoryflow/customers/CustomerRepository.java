package com.factoryflow.customers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Customer} entities.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /** Filter customers by type (RETAILER or DISTRIBUTOR). */
    List<Customer> findByType(String type);

    /** Return all active customers. */
    List<Customer> findByActiveTrue();

    /** Case-insensitive partial name search. */
    List<Customer> findByNameContainingIgnoreCase(String name);

    /** Return all customers assigned to a given salesman. */
    List<Customer> findByAssignedSalesmanId(UUID salesmanId);
}
