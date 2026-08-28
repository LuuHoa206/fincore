package com.luuhoa.fincore.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;

import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(UserAccount actor, String action, String entityType, UUID entityId, Map<String, Object> details) {
        auditLogRepository.save(new AuditLog(actor, action, entityType, entityId, MDC.get("correlationId"), details));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listForUser(UUID userId, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return auditLogRepository.findByActorUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
