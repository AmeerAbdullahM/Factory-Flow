package com.factoryflow.audit;

import com.factoryflow.users.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 50)
    private String username;

    @Column(length = 50)
    private String role;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // Getters (no setters - audit log is immutable)
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Map<String, Object> getOldValues() { return oldValues; }
    public Map<String, Object> getNewValues() { return newValues; }
    public String getReferenceNumber() { return referenceNumber; }
    public Instant getCreatedAt() { return createdAt; }

    // Builder for safe creation
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AuditLog log = new AuditLog();

        public Builder user(User u) { log.user = u; log.username = u != null ? u.getUsername() : null; return this; }
        public Builder role(String r) { log.role = r; return this; }
        public Builder action(String a) { log.action = a; return this; }
        public Builder entity(String type, UUID id) { log.entityType = type; log.entityId = id; return this; }
        public Builder oldValues(Map<String, Object> v) { log.oldValues = v; return this; }
        public Builder newValues(Map<String, Object> v) { log.newValues = v; return this; }
        public Builder reference(String ref) { log.referenceNumber = ref; return this; }
        public AuditLog build() { return log; }
    }
}
