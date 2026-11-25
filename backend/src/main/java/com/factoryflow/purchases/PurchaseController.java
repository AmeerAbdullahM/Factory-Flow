package com.factoryflow.purchases;

import com.factoryflow.purchases.PurchaseService.CreatePurchaseRequest;
import com.factoryflow.purchases.PurchaseService.PurchaseDto;
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
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    public ResponseEntity<List<PurchaseDto>> list() {
        return ResponseEntity.ok(purchaseService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','ACCOUNTANT','OWNER')")
    public ResponseEntity<PurchaseDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    public ResponseEntity<PurchaseDto> create(@Valid @RequestBody CreatePurchaseRequest req,
                                              @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.createPurchase(req, user));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    public ResponseEntity<PurchaseDto> receive(@PathVariable UUID id,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(purchaseService.receivePurchase(id, user));
    }
}
