package com.factoryflow.audit;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('OWNER','ACCOUNTANT','ADMIN')")
public class AuditController {

    private final AuditLogRepository auditRepo;

    public AuditController(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogDto>> list() {
        var logs = auditRepo.findAll().stream()
                .sorted(Comparator.comparing(AuditLog::getCreatedAt).reversed())
                .limit(200)
                .map(a -> new AuditLogDto(
                        a.getId(), a.getUsername(), a.getRole(), a.getAction(),
                        a.getEntityType(), a.getEntityId(), a.getOldValues(),
                        a.getNewValues(), a.getReferenceNumber(), a.getCreatedAt()
                )).toList();
        return ResponseEntity.ok(logs);
    }

    public record AuditLogDto(UUID id, String username, String role, String action,
                              String entityType, UUID entityId, Map<String, Object> oldValues,
                              Map<String, Object> newValues, String referenceNumber, Instant createdAt) {}
}
