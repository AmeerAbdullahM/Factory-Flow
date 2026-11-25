package com.factoryflow.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductionCostRepository extends JpaRepository<ProductionCost, UUID> {
    List<ProductionCost> findByProductionRun_Id(UUID runId);
}
