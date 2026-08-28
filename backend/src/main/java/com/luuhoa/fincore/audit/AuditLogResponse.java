package com.luuhoa.fincore.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record AuditLogResponse(UUID id, String action, String entityType, UUID entityId, Map<String, Object> details, Instant createdAt) {
    static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(auditLog.getId(), auditLog.getAction(), auditLog.getEntityType(), auditLog.getEntityId(), auditLog.getDetails(), auditLog.getCreatedAt());
    }
}
