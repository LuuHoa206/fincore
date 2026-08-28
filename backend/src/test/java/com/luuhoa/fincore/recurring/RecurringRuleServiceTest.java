package com.luuhoa.fincore.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.transaction.TransactionType;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletService;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecurringRuleServiceTest {

    @Mock private RecurringRuleRepository recurringRuleRepository;
    @Mock private UserAccountRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private CategoryService categoryService;
    @Mock private RecurringRuleExecutionService executionService;
    @Mock private AuditLogService auditLogService;

    @Test
    void upcomingReturnsEnabledRulesInTheRequestedBoundedPage() {
        UUID userId = UUID.randomUUID();
        RecurringRule rule = rule(userId, Instant.parse("2026-09-01T01:00:00Z"));
        when(recurringRuleRepository.findEnabledByUserIdWithDetails(eq(userId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(rule));

        List<RecurringRuleResponse> result = service().upcoming(userId, 4);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(recurringRuleRepository).findEnabledByUserIdWithDetails(eq(userId), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(4);
        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(rule.getId());
            assertThat(response.name()).isEqualTo("Monthly rent");
            assertThat(response.enabled()).isTrue();
        });
    }

    @Test
    void upcomingRejectsAnUnsafeLimit() {
        assertThatIllegalArgumentException().isThrownBy(() -> service().upcoming(UUID.randomUUID(), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> service().upcoming(UUID.randomUUID(), 11));
    }

    @Test
    void recordsCreatedRuleAfterTheScheduleHasBeenValidated() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UserAccount user = new UserAccount("user@example.com", "hash", "User", "VND", "Asia/Ho_Chi_Minh");
        Wallet wallet = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        Category category = new Category(user, "Rent", CategoryType.EXPENSE, null, null);
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        CreateRecurringRuleRequest request = new CreateRecurringRuleRequest(
                "Monthly rent", wallet.getId(), category.getId(), TransactionType.EXPENSE,
                new BigDecimal("5000000"), "Rent payment", null, RecurringFrequency.MONTHLY,
                Instant.parse("2026-09-01T01:00:00Z"), false, true, false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletService.requireOwnedActiveWallet(userId, wallet.getId())).thenReturn(wallet);
        when(categoryService.requireAvailableForTransaction(userId, category.getId(), CategoryType.EXPENSE)).thenReturn(category);
        when(recurringRuleRepository.save(any(RecurringRule.class))).thenAnswer(invocation -> {
            RecurringRule saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ruleId);
            return saved;
        });

        RecurringRuleResponse response = service().create(userId, request);

        assertThat(response.id()).isEqualTo(ruleId);
        verify(auditLogService).record(eq(user), eq("RECURRING_RULE_CREATED"), eq("RECURRING_RULE"), eq(ruleId), anyMap());
    }

    private RecurringRuleService service() {
        return new RecurringRuleService(recurringRuleRepository, userRepository, walletService, categoryService, executionService, auditLogService);
    }

    private RecurringRule rule(UUID userId, Instant nextRunAt) {
        UserAccount user = new UserAccount("user@example.com", "hash", "User", "VND", "Asia/Ho_Chi_Minh");
        ReflectionTestUtils.setField(user, "id", userId);
        Wallet wallet = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        Category category = new Category(user, "Rent", CategoryType.EXPENSE, null, null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        RecurringRule rule = new RecurringRule(
                user, wallet, category, "Monthly rent", TransactionType.EXPENSE,
                new BigDecimal("5000000"), "VND", "Rent payment", null,
                RecurringFrequency.MONTHLY, nextRunAt, false, true, false);
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        return rule;
    }
}
