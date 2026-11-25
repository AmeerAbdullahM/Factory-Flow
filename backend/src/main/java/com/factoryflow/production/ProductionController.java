package com.factoryflow.production;

import com.factoryflow.production.ProductionService.*;
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
@RequestMapping("/api/production")
public class ProductionController {

    private final ProductionService productionService;

    public ProductionController(ProductionService productionService) {
        this.productionService = productionService;
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<ProductionRunDto>> list() {
        return ResponseEntity.ok(productionService.getAll());
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<ProductionRunDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(productionService.getById(id));
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAnyRole('PRODUCTION_SUPERVISOR','ADMIN')")
    public ResponseEntity<ProductionRunDto> create(@Valid @RequestBody CreateProductionRunRequest req,
                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productionService.createRun(req, user));
    }

    @PutMapping("/runs/{id}/actuals")
    @PreAuthorize("hasAnyRole('PRODUCTION_SUPERVISOR','ADMIN')")
    public ResponseEntity<ProductionRunDto> updateActuals(@PathVariable UUID id,
                                                          @Valid @RequestBody UpdateActualsRequest req,
                                                          @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(productionService.updateActuals(id, req, user));
    }

    @PostMapping("/runs/{id}/post")
    @PreAuthorize("hasAnyRole('PRODUCTION_SUPERVISOR','ADMIN')")
    public ResponseEntity<ProductionRunDto> post(@PathVariable UUID id,
                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(productionService.postRun(id, user));
    }

    @PostMapping("/runs/{id}/costs")
    @PreAuthorize("hasAnyRole('PRODUCTION_SUPERVISOR','ADMIN')")
    public ResponseEntity<ProductionRunDto> addCost(@PathVariable UUID id,
                                                    @Valid @RequestBody AddCostRequest req,
                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(productionService.addOverheadCost(id, req, user));
    }
}
