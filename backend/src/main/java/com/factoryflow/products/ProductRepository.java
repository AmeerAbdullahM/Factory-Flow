package com.factoryflow.products;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Product} entities.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Exact code lookup (unique). */
    Optional<Product> findByCode(String code);

    /** Retrieve all products in a given category. */
    List<Product> findByCategory(String category);

    /** Retrieve all currently active products. */
    List<Product> findByActiveTrue();

    /** Case-insensitive partial name search for autocomplete / search UI. */
    List<Product> findByNameContainingIgnoreCase(String name);
}
