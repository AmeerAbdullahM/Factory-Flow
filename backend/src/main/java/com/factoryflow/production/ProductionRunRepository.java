package com.factoryflow.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionRunRepository extends JpaRepository<ProductionRun, UUID> {
    Optional<ProductionRun> findByReferenceNumber(String referenceNumber);
    List<ProductionRun> findByProduct_Id(UUID productId);
    List<ProductionRun> findByStatus(String status);
    List<ProductionRun> findByRunDateBetween(LocalDate from, LocalDate to);
    List<ProductionRun> findAllByOrderByCreatedAtDesc();
}
