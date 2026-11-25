package com.factoryflow.suppliers;

import com.factoryflow.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository supplierRepo;

    public SupplierController(SupplierRepository supplierRepo) {
        this.supplierRepo = supplierRepo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<SupplierDto>> list() {
        return ResponseEntity.ok(supplierRepo.findByActiveTrue().stream().map(this::toDto).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<SupplierDto> get(@PathVariable UUID id) {
        Supplier s = supplierRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Supplier not found: " + id, HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(toDto(s));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER')")
    public ResponseEntity<SupplierDto> create(@Valid @RequestBody CreateSupplierRequest req) {
        Supplier s = new Supplier();
        s.setName(req.name());
        s.setContactPerson(req.contactPerson());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setAddress(req.address());
        s.setCity(req.city());
        s.setState(req.state());
        s.setStateCode(req.stateCode());
        s.setGstin(req.gstin());
        s.setActive(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(supplierRepo.save(s)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER')")
    public ResponseEntity<SupplierDto> update(@PathVariable UUID id, @Valid @RequestBody CreateSupplierRequest req) {
        Supplier s = supplierRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Supplier not found: " + id, HttpStatus.NOT_FOUND));
        s.setName(req.name());
        s.setContactPerson(req.contactPerson());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setAddress(req.address());
        s.setCity(req.city());
        s.setState(req.state());
        s.setStateCode(req.stateCode());
        s.setGstin(req.gstin());
        return ResponseEntity.ok(toDto(supplierRepo.save(s)));
    }

    private SupplierDto toDto(Supplier s) {
        return new SupplierDto(s.getId(), s.getName(), s.getContactPerson(), s.getPhone(),
                s.getEmail(), s.getAddress(), s.getCity(), s.getState(), s.getStateCode(), s.getGstin(), s.isActive());
    }

    public record SupplierDto(UUID id, String name, String contactPerson, String phone, String email,
                              String address, String city, String state, String stateCode, String gstin, boolean active) {}

    public record CreateSupplierRequest(@NotBlank String name, String contactPerson, String phone,
                                        String email, String address, String city, String state,
                                        String stateCode, String gstin) {}
}
