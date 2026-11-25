package com.factoryflow.customers;

import com.factoryflow.audit.AuditService;
import com.factoryflow.common.BusinessException;
import com.factoryflow.users.User;
import com.factoryflow.users.UserRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepo, UserRepository userRepo, AuditService auditService) {
        this.customerRepo = customerRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    public List<CustomerDto> findForUser(User user, String search) {
        List<Customer> list;
        if (user.hasRole("SALESMAN") && !user.hasRole("ADMIN") && !user.hasRole("OWNER")) {
            list = customerRepo.findByAssignedSalesmanId(user.getId());
        } else if (search != null && !search.isBlank()) {
            list = customerRepo.findByNameContainingIgnoreCase(search);
        } else {
            list = customerRepo.findByActiveTrue();
        }
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            list = list.stream().filter(c -> c.getName().toLowerCase().contains(q)).toList();
        }
        return list.stream().map(this::toDto).toList();
    }

    public CustomerDto findById(UUID id, User user) {
        Customer c = getEntity(id);
        if (user.hasRole("SALESMAN") && !user.hasRole("ADMIN") && !user.hasRole("OWNER")) {
            if (c.getAssignedSalesman() == null || !c.getAssignedSalesman().getId().equals(user.getId())) {
                throw new BusinessException("Access denied to customer", HttpStatus.FORBIDDEN);
            }
        }
        return toDto(c);
    }

    public Customer getEntity(UUID id) {
        return customerRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Customer not found: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public CustomerDto create(CreateCustomerRequest req, User user) {
        Customer c = new Customer();
        applyRequest(c, req);
        c.setActive(true);
        Customer saved = customerRepo.save(c);
        auditService.log(user, "CUSTOMER_CREATED", "CUSTOMER", saved.getId(),
                null, Map.of("name", saved.getName(), "type", saved.getType()), saved.getName());
        return toDto(saved);
    }

    @Transactional
    public CustomerDto update(UUID id, CreateCustomerRequest req, User user) {
        Customer c = getEntity(id);
        applyRequest(c, req);
        Customer saved = customerRepo.save(c);
        auditService.log(user, "CUSTOMER_UPDATED", "CUSTOMER", saved.getId(),
                null, Map.of("name", saved.getName()), saved.getName());
        return toDto(saved);
    }

    private void applyRequest(Customer c, CreateCustomerRequest req) {
        c.setName(req.name());
        c.setType(req.type());
        c.setGstin(req.gstin());
        c.setBillingAddress(req.billingAddress());
        c.setCity(req.city());
        c.setState(req.state());
        c.setStateCode(req.stateCode());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setCreditLimit(req.creditLimit() != null ? req.creditLimit() : BigDecimal.ZERO);
        c.setPaymentTermsDays(req.paymentTermsDays() > 0 ? req.paymentTermsDays() : 30);
        if (req.assignedSalesmanId() != null) {
            User salesman = userRepo.findById(req.assignedSalesmanId())
                    .orElseThrow(() -> new BusinessException("Salesman not found", HttpStatus.NOT_FOUND));
            c.setAssignedSalesman(salesman);
        } else {
            c.setAssignedSalesman(null);
        }
    }

    public CustomerDto toDto(Customer c) {
        return new CustomerDto(
                c.getId(), c.getName(), c.getType(), c.getGstin(),
                c.getBillingAddress(), c.getCity(), c.getState(), c.getStateCode(),
                c.getPhone(), c.getEmail(), c.getCreditLimit(), c.getPaymentTermsDays(),
                c.getAssignedSalesman() != null ? c.getAssignedSalesman().getId() : null,
                c.getAssignedSalesman() != null ? c.getAssignedSalesman().getFullName() : null,
                c.isActive()
        );
    }

    public record CustomerDto(
            UUID id, String name, String type, String gstin, String billingAddress,
            String city, String state, String stateCode, String phone, String email,
            BigDecimal creditLimit, int paymentTermsDays, UUID assignedSalesmanId,
            String assignedSalesmanName, boolean active) {}

    public record CreateCustomerRequest(
            @NotBlank String name,
            @NotBlank String type,
            String gstin,
            String billingAddress,
            String city,
            @NotBlank String state,
            @NotBlank String stateCode,
            String phone,
            String email,
            BigDecimal creditLimit,
            int paymentTermsDays,
            UUID assignedSalesmanId) {}
}
