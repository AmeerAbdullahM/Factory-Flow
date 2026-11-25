package com.factoryflow.payments;

import com.factoryflow.payments.PaymentService.*;
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
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public ResponseEntity<List<PaymentDto>> list() {
        return ResponseEntity.ok(paymentService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentDto> record(@Valid @RequestBody CreatePaymentRequest req,
                                             @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.recordPayment(req, user));
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public ResponseEntity<List<OutstandingDto>> allOutstanding() {
        return ResponseEntity.ok(paymentService.getAllOutstanding());
    }

    @GetMapping("/outstanding/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER','SALESMAN')")
    public ResponseEntity<List<OutstandingDto>> customerOutstanding(@PathVariable UUID customerId) {
        return ResponseEntity.ok(paymentService.getOutstandingForCustomer(customerId));
    }
}
