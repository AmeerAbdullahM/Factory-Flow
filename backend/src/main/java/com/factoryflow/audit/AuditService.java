package com.factoryflow.audit;

import com.factoryflow.users.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service for creating immutable audit log entries.
 * Uses REQUIRES_NEW propagation so audit entries are saved even if the outer transaction rolls back
 * (for failure auditing). Normal success entries are within the same transaction.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(User user, String action, String entityType, UUID entityId,
                    Map<String, Object> oldValues, Map<String, Object> newValues,
                    String referenceNumber) {
        String role = user != null && !user.getRoles().isEmpty()
            ? user.getRoles().iterator().next().getName() : "SYSTEM";

        AuditLog entry = AuditLog.builder()
            .user(user)
            .role(role)
            .action(action)
            .entity(entityType, entityId)
            .oldValues(oldValues)
            .newValues(newValues)
            .reference(referenceNumber)
            .build();

        auditLogRepository.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(User user, String action, String entityType, UUID entityId, String referenceNumber) {
        log(user, action, entityType, entityId, null, null, referenceNumber);
    }
}
