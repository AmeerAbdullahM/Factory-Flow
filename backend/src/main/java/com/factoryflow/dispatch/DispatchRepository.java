package com.factoryflow.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DispatchRepository extends JpaRepository<Dispatch, UUID> {
    List<Dispatch> findByInvoice_Id(UUID invoiceId);
    List<Dispatch> findByCustomer_Id(UUID customerId);
    List<Dispatch> findAllByOrderByCreatedAtDesc();
}
