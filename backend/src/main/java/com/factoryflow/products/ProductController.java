package com.factoryflow.products;

import com.factoryflow.products.ProductService.CreateProductRequest;
import com.factoryflow.products.ProductService.ProductDto;
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
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<List<ProductDto>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(productService.findAll(includeInactive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ProductDto> create(@Valid @RequestBody CreateProductRequest req,
                                             @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(req, user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ProductDto> update(@PathVariable UUID id,
                                             @Valid @RequestBody CreateProductRequest req,
                                             @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(productService.update(id, req, user));
    }
}
