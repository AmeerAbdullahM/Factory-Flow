package com.factoryflow.materials;

import com.factoryflow.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for raw materials, packaging materials, and units of measurement.
 *
 * <p>All state-changing operations (POST, PUT) are restricted to ADMIN and
 * PRODUCTION_SUPERVISOR.  Read access is granted to all authenticated staff roles.</p>
 *
 * <p>Base path: {@code /api/materials}</p>
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialsController {

    // -------------------------------------------------------------------------
    // Inner DTOs (Java records — immutable, no-boilerplate)
    // -------------------------------------------------------------------------

    /** Read DTO for a raw material.  UnitId exposed alongside symbol for client lookups. */
    public record RawMaterialDto(
            UUID id,
            String name,
            String code,
            String description,
            String unitSymbol,
            UUID unitId,
            BigDecimal reorderLevel,
            boolean active) {}

    /** Read DTO for a packaging material. */
    public record PackagingMaterialDto(
            UUID id,
            String name,
            String code,
            String description,
            String unitSymbol,
            UUID unitId,
            BigDecimal reorderLevel,
            boolean active) {}

    /** Request body for creating or updating a raw material. */
    public record CreateRawMaterialRequest(
            @NotBlank String name,
            @NotBlank String code,
            String description,
            @NotNull UUID unitId,
            BigDecimal reorderLevel) {}

    /** Request body for creating or updating a packaging material. */
    public record CreatePackagingMaterialRequest(
            @NotBlank String name,
            @NotBlank String code,
            String description,
            @NotNull UUID unitId,
            BigDecimal reorderLevel) {}

    /** Read DTO for a unit of measurement. */
    public record UnitDto(
            UUID id,
            String name,
            String symbol,
            String unitType,
            BigDecimal toBaseFactor) {}

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final RawMaterialRepository rawMaterialRepository;
    private final PackagingMaterialRepository packagingMaterialRepository;
    private final UnitRepository unitRepository;

    public MaterialsController(RawMaterialRepository rawMaterialRepository,
                               PackagingMaterialRepository packagingMaterialRepository,
                               UnitRepository unitRepository) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.packagingMaterialRepository = packagingMaterialRepository;
        this.unitRepository = unitRepository;
    }

    // =========================================================================
    // RAW MATERIALS
    // =========================================================================

    /**
     * List all raw materials.
     * Accessible by: ADMIN, PRODUCTION_SUPERVISOR, OWNER, ACCOUNTANT.
     */
    @GetMapping("/raw")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<RawMaterialDto>> listRawMaterials() {
        List<RawMaterialDto> dtos = rawMaterialRepository.findAll()
                .stream()
                .map(this::toRawDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a single raw material by ID.
     * Accessible by: ADMIN, PRODUCTION_SUPERVISOR, OWNER, ACCOUNTANT.
     */
    @GetMapping("/raw/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<RawMaterialDto> getRawMaterial(@PathVariable UUID id) {
        RawMaterial rm = rawMaterialRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Raw material not found: " + id, HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(toRawDto(rm));
    }

    /**
     * Create a new raw material.
     * Restricted to: ADMIN, PRODUCTION_SUPERVISOR.
     */
    @PostMapping("/raw")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    public ResponseEntity<RawMaterialDto> createRawMaterial(
            @Valid @RequestBody CreateRawMaterialRequest request) {

        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new BusinessException("Unit not found: " + request.unitId(), HttpStatus.BAD_REQUEST));

        RawMaterial rm = new RawMaterial();
        rm.setName(request.name());
        rm.setCode(request.code());
        rm.setDescription(request.description());
        rm.setUnit(unit);
        rm.setReorderLevel(request.reorderLevel());
        rm.setActive(true);

        RawMaterial saved = rawMaterialRepository.save(rm);
        return ResponseEntity.status(HttpStatus.CREATED).body(toRawDto(saved));
    }

    /**
     * Update an existing raw material.
     * Restricted to: ADMIN, PRODUCTION_SUPERVISOR.
     */
    @PutMapping("/raw/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    public ResponseEntity<RawMaterialDto> updateRawMaterial(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRawMaterialRequest request) {

        RawMaterial rm = rawMaterialRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Raw material not found: " + id, HttpStatus.NOT_FOUND));

        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new BusinessException("Unit not found: " + request.unitId(), HttpStatus.BAD_REQUEST));

        rm.setName(request.name());
        rm.setCode(request.code());
        rm.setDescription(request.description());
        rm.setUnit(unit);
        rm.setReorderLevel(request.reorderLevel());

        RawMaterial saved = rawMaterialRepository.save(rm);
        return ResponseEntity.ok(toRawDto(saved));
    }

    // =========================================================================
    // PACKAGING MATERIALS
    // =========================================================================

    /**
     * List all packaging materials.
     * Accessible by: ADMIN, PRODUCTION_SUPERVISOR, OWNER, ACCOUNTANT.
     */
    @GetMapping("/packaging")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR','OWNER','ACCOUNTANT')")
    public ResponseEntity<List<PackagingMaterialDto>> listPackagingMaterials() {
        List<PackagingMaterialDto> dtos = packagingMaterialRepository.findAll()
                .stream()
                .map(this::toPackagingDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Create a new packaging material.
     * Restricted to: ADMIN, PRODUCTION_SUPERVISOR.
     */
    @PostMapping("/packaging")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    public ResponseEntity<PackagingMaterialDto> createPackagingMaterial(
            @Valid @RequestBody CreatePackagingMaterialRequest request) {

        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new BusinessException("Unit not found: " + request.unitId(), HttpStatus.BAD_REQUEST));

        PackagingMaterial pm = new PackagingMaterial();
        pm.setName(request.name());
        pm.setCode(request.code());
        pm.setDescription(request.description());
        pm.setUnit(unit);
        pm.setReorderLevel(request.reorderLevel());
        pm.setActive(true);

        PackagingMaterial saved = packagingMaterialRepository.save(pm);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPackagingDto(saved));
    }

    /**
     * Update an existing packaging material.
     * Restricted to: ADMIN, PRODUCTION_SUPERVISOR.
     */
    @PutMapping("/packaging/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCTION_SUPERVISOR')")
    public ResponseEntity<PackagingMaterialDto> updatePackagingMaterial(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePackagingMaterialRequest request) {

        PackagingMaterial pm = packagingMaterialRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Packaging material not found: " + id, HttpStatus.NOT_FOUND));

        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new BusinessException("Unit not found: " + request.unitId(), HttpStatus.BAD_REQUEST));

        pm.setName(request.name());
        pm.setCode(request.code());
        pm.setDescription(request.description());
        pm.setUnit(unit);
        pm.setReorderLevel(request.reorderLevel());

        PackagingMaterial saved = packagingMaterialRepository.save(pm);
        return ResponseEntity.ok(toPackagingDto(saved));
    }

    // =========================================================================
    // UNITS
    // =========================================================================

    /**
     * List all units of measurement.
     * Accessible by all authenticated roles (units are reference data).
     */
    @GetMapping("/units")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UnitDto>> listUnits() {
        List<UnitDto> dtos = unitRepository.findAll()
                .stream()
                .map(this::toUnitDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // =========================================================================
    // MAPPING HELPERS
    // =========================================================================

    private RawMaterialDto toRawDto(RawMaterial rm) {
        return new RawMaterialDto(
                rm.getId(),
                rm.getName(),
                rm.getCode(),
                rm.getDescription(),
                rm.getUnit() != null ? rm.getUnit().getSymbol() : null,
                rm.getUnit() != null ? rm.getUnit().getId()     : null,
                rm.getReorderLevel(),
                rm.isActive());
    }

    private PackagingMaterialDto toPackagingDto(PackagingMaterial pm) {
        return new PackagingMaterialDto(
                pm.getId(),
                pm.getName(),
                pm.getCode(),
                pm.getDescription(),
                pm.getUnit() != null ? pm.getUnit().getSymbol() : null,
                pm.getUnit() != null ? pm.getUnit().getId()     : null,
                pm.getReorderLevel(),
                pm.isActive());
    }

    private UnitDto toUnitDto(Unit u) {
        return new UnitDto(
                u.getId(),
                u.getName(),
                u.getSymbol(),
                u.getUnitType(),
                u.getToBaseFactor());
    }
}
