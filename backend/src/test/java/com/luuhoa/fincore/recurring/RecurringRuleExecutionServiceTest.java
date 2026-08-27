package com.luuhoa.fincore.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.transaction.CreateTransactionRequest;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.transaction.TransactionType;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecurringRuleExecutionServiceTest {

    @Mock private RecurringRuleRepository recurringRuleRepository;
    @Mock private TransactionService transactionService;

    @Test
    void autoRecordingDueRulePostsOnceWithDeterministicIdempotencyKeyAndAdvancesSchedule() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-08-01T01:00:00Z");
        Instant now = Instant.parse("2026-08-03T02:00:00Z");
        RecurringRule rule = rule(userId, ruleId, scheduledAt);
        when(recurringRuleRepository.findByIdForUpdate(ruleId)).thenReturn(Optional.of(rule));

        RecurringRuleExecutionService service = new RecurringRuleExecutionService(recurringRuleRepository, transactionService);

        assertThat(service.autoRecordDueRule(ruleId, now)).isTrue();

        ArgumentCaptor<CreateTransactionRequest> request = ArgumentCaptor.forClass(CreateTransactionRequest.class);
        verify(transactionService).create(eq(userId), request.capture(), eq("recurring:" + ruleId + ":" + scheduledAt.toEpochMilli()));
        assertThat(request.getValue().occurredAt()).isEqualTo(scheduledAt);
        assertThat(rule.getNextRunAt()).isEqualTo(Instant.parse("2026-08-04T01:00:00Z"));
    }

    @Test
    void skipsRuleThatIsNoLongerDueWhenAnotherWorkerAlreadyAdvancedIt() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        RecurringRule rule = rule(userId, ruleId, Instant.parse("2026-08-04T01:00:00Z"));
        when(recurringRuleRepository.findByIdForUpdate(ruleId)).thenReturn(Optional.of(rule));

        RecurringRuleExecutionService service = new RecurringRuleExecutionService(recurringRuleRepository, transactionService);

        assertThat(service.autoRecordDueRule(ruleId, Instant.parse("2026-08-03T02:00:00Z"))).isFalse();
        verify(transactionService, org.mockito.Mockito.never()).create(any(), any(), any());
    }

    private RecurringRule rule(UUID userId, UUID ruleId, Instant nextRunAt) {
        UserAccount user = new UserAccount("user@example.com", "hash", "User", "VND", "Asia/Ho_Chi_Minh");
        ReflectionTestUtils.setField(user, "id", userId);
        Wallet wallet = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        Category category = new Category(user, "Rent", CategoryType.EXPENSE, null, null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        RecurringRule rule = new RecurringRule(
                user,
                wallet,
                category,
                "Monthly rent",
                TransactionType.EXPENSE,
                new BigDecimal("5000000"),
                "VND",
                "Rent payment",
                null,
                RecurringFrequency.DAILY,
                nextRunAt,
                true,
                true,
                false);
        ReflectionTestUtils.setField(rule, "id", ruleId);
        return rule;
    }
}
