package com.factoryflow.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {
    Optional<SalesOrder> findByOrderNumber(String orderNumber);
    List<SalesOrder> findBySalesman_IdOrderByCreatedAtDesc(UUID salesmanId);
    List<SalesOrder> findByCustomer_Id(UUID customerId);
    List<SalesOrder> findByStatusOrderByCreatedAtDesc(String status);
    List<SalesOrder> findByOrderDateBetween(LocalDate from, LocalDate to);
    List<SalesOrder> findAllByOrderByCreatedAtDesc();
}
