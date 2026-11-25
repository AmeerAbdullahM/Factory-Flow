package com.factoryflow.customers;

import com.factoryflow.customers.CustomerService.CreateCustomerRequest;
import com.factoryflow.customers.CustomerService.CustomerDto;
import com.factoryflow.users.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<List<CustomerDto>> list(@AuthenticationPrincipal User user,
                                                  @RequestParam(required = false) String search) {
        return ResponseEntity.ok(customerService.findForUser(user, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerDto> get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(customerService.findById(id, user));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<CustomerDto> create(@Valid @RequestBody CreateCustomerRequest req,
                                              @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(req, user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<CustomerDto> update(@PathVariable UUID id,
                                              @Valid @RequestBody CreateCustomerRequest req,
                                              @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(customerService.update(id, req, user));
    }
}
