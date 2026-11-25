package com.factoryflow.sales;

import com.factoryflow.sales.SalesOrderService.*;
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
@RequestMapping("/api/orders")
public class SalesOrderController {

    private final SalesOrderService orderService;

    public SalesOrderController(SalesOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<SalesOrderDto>> list(@AuthenticationPrincipal User user,
                                                    @RequestParam(required = false) String status) {
        return ResponseEntity.ok(orderService.getOrdersForUser(user, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalesOrderDto> get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.getOrder(id, user));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SALESMAN','ADMIN')")
    public ResponseEntity<SalesOrderDto> create(@Valid @RequestBody CreateOrderRequest req,
                                                @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(req, user));
    }

    @GetMapping("/{id}/stock-check")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<List<StockCheckResult>> stockCheck(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getStockAvailability(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SalesOrderDto> approve(@PathVariable UUID id,
                                                 @RequestBody(required = false) ApproveOrderRequest req,
                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.approveOrder(id, req, user));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SalesOrderDto> reject(@PathVariable UUID id,
                                                @RequestBody(required = false) RejectRequest req,
                                                @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.rejectOrder(id, req != null ? req.reason() : "", user));
    }

    public record RejectRequest(String reason) {}
}
