package com.luuhoa.fincore.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.recurring.RecurringFrequency;
import com.luuhoa.fincore.recurring.RecurringRule;
import com.luuhoa.fincore.recurring.RecurringRuleRepository;
import com.luuhoa.fincore.transaction.TransactionType;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CashFlowForecastServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private UserAccountRepository userRepository;
    @Mock private RecurringRuleRepository recurringRuleRepository;

    private UserAccount user;
    private CashFlowForecastService service;

    @BeforeEach
    void setUp() {
        user = new UserAccount("user@example.com", "hash", "User", "VND", "Asia/Ho_Chi_Minh");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        service = new CashFlowForecastService(userRepository, recurringRuleRepository);
    }

    @Test
    void forecastsOnlyEnabledRulesWithinTheRequestedHorizonAndGroupsMoneyByCurrency() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        RecurringRule dailyExpense = rule("Coffee", TransactionType.EXPENSE, "10000", RecurringFrequency.DAILY, "2026-08-02T00:00:00Z");
        RecurringRule monthlyIncome = rule("Salary", TransactionType.INCOME, "1000000", RecurringFrequency.MONTHLY, "2026-08-05T00:00:00Z");
        when(recurringRuleRepository.findAllEnabledByUserIdWithDetails(USER_ID)).thenReturn(List.of(dailyExpense, monthlyIncome));

        CashFlowForecastResponse response = service.forecast(USER_ID, 7, Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(response.days()).isEqualTo(7);
        assertThat(response.upcomingOccurrences()).hasSize(7);
        assertThat(response.upcomingOccurrences()).extracting(CashFlowForecastOccurrence::ruleName)
                .containsExactly("Coffee", "Coffee", "Coffee", "Coffee", "Salary", "Coffee", "Coffee");
        assertThat(response.currencySummaries()).singleElement().satisfies(summary -> {
            assertThat(summary.currency()).isEqualTo("VND");
            assertThat(summary.projectedIncome()).isEqualByComparingTo("1000000");
            assertThat(summary.projectedExpense()).isEqualByComparingTo("60000");
            assertThat(summary.projectedNet()).isEqualByComparingTo("940000");
        });
    }

    @Test
    void rejectsAnOverlyShortOrLongForecastHorizon() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.forecast(USER_ID, 6, Instant.now()));
        assertThatIllegalArgumentException().isThrownBy(() -> service.forecast(USER_ID, 91, Instant.now()));
    }

    private RecurringRule rule(
            String name,
            TransactionType type,
            String amount,
            RecurringFrequency frequency,
            String nextRunAt) {
        Wallet wallet = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        Category category = new Category(user, type == TransactionType.INCOME ? "Income" : "Food", type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE, null, null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        RecurringRule rule = new RecurringRule(
                user, wallet, category, name, type, new BigDecimal(amount), "VND", name, null,
                frequency, Instant.parse(nextRunAt), false, true, false);
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        return rule;
    }
}
