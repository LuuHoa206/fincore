package com.luuhoa.fincore.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.budget.BudgetResponse;
import com.luuhoa.fincore.budget.BudgetService;
import com.luuhoa.fincore.budget.BudgetStatus;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.recurring.RecurringFrequency;
import com.luuhoa.fincore.recurring.RecurringRuleResponse;
import com.luuhoa.fincore.recurring.RecurringRuleService;
import com.luuhoa.fincore.transaction.TransactionType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationReadStateRepository readStateRepository;
    @Mock private UserAccountRepository userRepository;
    @Mock private RecurringRuleService recurringRuleService;
    @Mock private BudgetService budgetService;
    @Mock private AuditLogService auditLogService;

    @Test
    void listsCurrentBudgetAndRecurringAttentionItemsWithReadState() {
        UUID userId = UUID.randomUUID();
        UserAccount user = user(userId);
        Instant dueAt = Instant.now().minusSeconds(60);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(recurringRuleService.upcoming(userId, 10)).thenReturn(List.of(recurring(dueAt)));
        when(budgetService.list(userId, null)).thenReturn(List.of(budget(BudgetStatus.EXCEEDED)));
        when(readStateRepository.findAllByUserIdAndNotificationKeyIn(eq(userId), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());

        List<FinancialNotificationResponse> result = service().list(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FinancialNotificationResponse::priority)
                .containsExactly(NotificationPriority.CRITICAL, NotificationPriority.WARNING);
        assertThat(result).allSatisfy(notification -> assertThat(notification.read()).isFalse());
        assertThat(result).extracting(FinancialNotificationResponse::destination)
                .containsExactly("/budgets", "/recurring");
    }

    @Test
    void marksOnlyAStillCurrentNotificationAsRead() {
        UUID userId = UUID.randomUUID();
        UserAccount user = user(userId);
        RecurringRuleResponse rule = recurring(Instant.now().plusSeconds(60));
        String key = "recurring:" + rule.id() + ":" + rule.nextRunAt().toEpochMilli();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(recurringRuleService.upcoming(userId, 10)).thenReturn(List.of(rule));
        when(budgetService.list(userId, null)).thenReturn(List.of());
        when(readStateRepository.insertIfAbsent(userId, key)).thenReturn(1);

        service().markRead(userId, new MarkNotificationReadRequest(key));

        verify(readStateRepository).insertIfAbsent(userId, key);
        verify(auditLogService).record(eq(user), eq("NOTIFICATION_MARKED_READ"), eq("NOTIFICATION"), eq(null), anyMap());
    }

    @Test
    void rejectsAReadRequestForAnAlertThatIsNoLongerCurrent() {
        UUID userId = UUID.randomUUID();
        UserAccount user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(recurringRuleService.upcoming(userId, 10)).thenReturn(List.of());
        when(budgetService.list(userId, null)).thenReturn(List.of());

        assertThatThrownBy(() -> service().markRead(userId, new MarkNotificationReadRequest("budget:missing")))
                .isInstanceOf(com.luuhoa.fincore.shared.api.ResourceNotFoundException.class);

        verifyNoInteractions(readStateRepository, auditLogService);
    }

    private NotificationService service() {
        return new NotificationService(readStateRepository, userRepository, recurringRuleService, budgetService, auditLogService);
    }

    private UserAccount user(UUID userId) {
        UserAccount user = new UserAccount("user@example.com", "hash", "User", "VND", "Asia/Ho_Chi_Minh");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private RecurringRuleResponse recurring(Instant nextRunAt) {
        return new RecurringRuleResponse(
                UUID.randomUUID(), "Tiền thuê nhà", UUID.randomUUID(), "Ngân hàng", UUID.randomUUID(), "Nhà ở",
                TransactionType.EXPENSE, new BigDecimal("5000000"), "VND", "Thanh toán tiền thuê", null,
                RecurringFrequency.MONTHLY, 1, nextRunAt, false, true, false, nextRunAt.minusSeconds(60), nextRunAt.minusSeconds(30));
    }

    private BudgetResponse budget(BudgetStatus status) {
        Instant updatedAt = Instant.now();
        return new BudgetResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Ăn uống", null, null, LocalDate.now().withDayOfMonth(1),
                new BigDecimal("2000000"), new BigDecimal("2200000"), new BigDecimal("-200000"),
                new BigDecimal("110"), "VND", 80, status, updatedAt.minusSeconds(60), updatedAt);
    }
}
