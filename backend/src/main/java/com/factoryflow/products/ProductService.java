package com.factoryflow.products;

import com.factoryflow.audit.AuditService;
import com.factoryflow.common.BusinessException;
import com.factoryflow.materials.Unit;
import com.factoryflow.materials.UnitRepository;
import com.factoryflow.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepo;
    private final UnitRepository unitRepo;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepo, UnitRepository unitRepo, AuditService auditService) {
        this.productRepo = productRepo;
        this.unitRepo = unitRepo;
        this.auditService = auditService;
    }

    public List<ProductDto> findAll(boolean includeInactive) {
        List<Product> list = includeInactive ? productRepo.findAll() : productRepo.findByActiveTrue();
        return list.stream().map(this::toDto).toList();
    }

    public ProductDto findById(UUID id) {
        return toDto(getEntity(id));
    }

    public Product getEntity(UUID id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Product not found: " + id, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ProductDto create(CreateProductRequest req, User user) {
        if (productRepo.findByCode(req.code()).isPresent()) {
            throw new BusinessException("Product code already exists: " + req.code(), HttpStatus.CONFLICT);
        }
        Unit unit = unitRepo.findById(req.unitId())
                .orElseThrow(() -> new BusinessException("Unit not found: " + req.unitId(), HttpStatus.NOT_FOUND));

        Product p = new Product();
        p.setName(req.name());
        p.setCode(req.code());
        p.setCategory(req.category());
        p.setPackSizeDescription(req.packSizeDescription());
        p.setPackSizeGrams(req.packSizeGrams());
        p.setSellingPrice(req.sellingPrice());
        p.setGstRate(req.gstRate() != null ? req.gstRate() : new BigDecimal("5.00"));
        p.setHsnCode(req.hsnCode());
        p.setUnit(unit);
        if (req.baseProductId() != null) {
            p.setBaseProduct(getEntity(req.baseProductId()));
        }
        p.setBomConversionFactor(req.bomConversionFactor() != null ? req.bomConversionFactor() : BigDecimal.ONE);
        p.setActive(true);

        Product saved = productRepo.save(p);
        auditService.log(user, "PRODUCT_CREATED", "PRODUCT", saved.getId(),
                null, Map.of("code", saved.getCode(), "price", saved.getSellingPrice()), saved.getCode());
        return toDto(saved);
    }

    @Transactional
    public ProductDto update(UUID id, CreateProductRequest req, User user) {
        Product p = getEntity(id);
        Unit unit = unitRepo.findById(req.unitId())
                .orElseThrow(() -> new BusinessException("Unit not found: " + req.unitId(), HttpStatus.NOT_FOUND));

        BigDecimal oldPrice = p.getSellingPrice();
        p.setName(req.name());
        p.setCode(req.code());
        p.setCategory(req.category());
        p.setPackSizeDescription(req.packSizeDescription());
        p.setPackSizeGrams(req.packSizeGrams());
        p.setSellingPrice(req.sellingPrice());
        if (req.gstRate() != null) p.setGstRate(req.gstRate());
        p.setHsnCode(req.hsnCode());
        p.setUnit(unit);
        if (req.baseProductId() != null) {
            p.setBaseProduct(getEntity(req.baseProductId()));
        } else {
            p.setBaseProduct(null);
        }
        if (req.bomConversionFactor() != null) {
            p.setBomConversionFactor(req.bomConversionFactor());
        }

        Product saved = productRepo.save(p);
        auditService.log(user, "PRODUCT_UPDATED", "PRODUCT", saved.getId(),
                Map.of("price", oldPrice), Map.of("price", saved.getSellingPrice()), saved.getCode());
        return toDto(saved);
    }

    public ProductDto toDto(Product p) {
        return new ProductDto(
                p.getId(),
                p.getName(),
                p.getCode(),
                p.getCategory(),
                p.getPackSizeDescription(),
                p.getPackSizeGrams(),
                p.getSellingPrice(),
                p.getGstRate(),
                p.getHsnCode(),
                p.getUnit() != null ? p.getUnit().getSymbol() : null,
                p.getUnit() != null ? p.getUnit().getId() : null,
                p.isActive(),
                p.getBomConversionFactor(),
                p.getBaseProduct() != null ? p.getBaseProduct().getId() : null
        );
    }

    public record ProductDto(
            UUID id, String name, String code, String category,
            String packSizeDescription, BigDecimal packSizeGrams,
            BigDecimal sellingPrice, BigDecimal gstRate, String hsnCode,
            String unitSymbol, UUID unitId, boolean active,
            BigDecimal bomConversionFactor, UUID baseProductId) {}

    public record CreateProductRequest(
            @jakarta.validation.constraints.NotBlank String name,
            @jakarta.validation.constraints.NotBlank String code,
            @jakarta.validation.constraints.NotBlank String category,
            String packSizeDescription,
            BigDecimal packSizeGrams,
            @jakarta.validation.constraints.NotNull BigDecimal sellingPrice,
            BigDecimal gstRate,
            String hsnCode,
            @jakarta.validation.constraints.NotNull UUID unitId,
            UUID baseProductId,
            BigDecimal bomConversionFactor) {}
}
