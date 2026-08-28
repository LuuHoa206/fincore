package com.luuhoa.fincore.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserAccount user;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogRepository);
    }

    @Test
    void recordsAnIndependentDetailSnapshot() {
        Map<String, Object> details = new LinkedHashMap<>(Map.of("currency", "VND"));

        service.record(user, "TRANSACTION_CREATED", "TRANSACTION", UUID.randomUUID(), details);
        details.put("currency", "USD");

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();
        assertThat(saved.getAction()).isEqualTo("TRANSACTION_CREATED");
        assertThat(saved.getEntityType()).isEqualTo("TRANSACTION");
        assertThat(saved.getDetails()).containsEntry("currency", "VND");
    }

    @Test
    void returnsNewestUserActivitiesWithinRequestedLimit() {
        UUID userId = UUID.randomUUID();
        AuditLog first = new AuditLog(user, "TRANSFER_CREATED", "TRANSACTION", UUID.randomUUID(), null, Map.of("currency", "VND"));
        AuditLog second = new AuditLog(user, "PROFILE_UPDATED", "USER", userId, null, Map.of("timeZone", "Asia/Ho_Chi_Minh"));
        when(auditLogRepository.findByActorUserIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        List<AuditLogResponse> response = service.listForUser(userId, 20);

        assertThat(response).extracting(AuditLogResponse::action)
                .containsExactly("TRANSFER_CREATED", "PROFILE_UPDATED");
        verify(auditLogRepository).findByActorUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 20));
    }

    @Test
    void rejectsUnsafeActivityLimits() {
        assertThatThrownBy(() -> service.listForUser(UUID.randomUUID(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
        assertThatThrownBy(() -> service.listForUser(UUID.randomUUID(), 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
    }
}
