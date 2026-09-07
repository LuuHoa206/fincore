package com.luuhoa.fincore.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.budget.BudgetResponse;
import com.luuhoa.fincore.budget.BudgetService;
import com.luuhoa.fincore.budget.BudgetStatus;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.moneyjar.MoneyJarResponse;
import com.luuhoa.fincore.moneyjar.MoneyJarService;
import com.luuhoa.fincore.savinggoal.SavingGoalResponse;
import com.luuhoa.fincore.savinggoal.SavingGoalService;
import com.luuhoa.fincore.savinggoal.SavingGoalStatus;
import com.luuhoa.fincore.transaction.TransactionReportingService;
import com.luuhoa.fincore.transaction.TransactionReportingService.CurrencyCashFlow;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.wallet.WalletResponse;
import com.luuhoa.fincore.wallet.WalletService;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private UserAccountRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private MoneyJarService moneyJarService;
    @Mock private BudgetService budgetService;
    @Mock private SavingGoalService savingGoalService;
    @Mock private TransactionReportingService transactionReportingService;
    @Mock private TransactionService transactionService;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                userRepository,
                walletService,
                moneyJarService,
                budgetService,
                savingGoalService,
                transactionReportingService,
                transactionService);
    }

    @Test
    void summarizesTheRequestedMonthWithoutMixingCurrencies() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(new UserAccount(
                "owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh")));
        when(walletService.list(userId)).thenReturn(List.of(
                wallet("Cash", "VND", "2000000"),
                wallet("Travel", "USD", "100")));
        when(moneyJarService.list(userId)).thenReturn(List.of(jar("Emergency", "VND", "800000")));
        when(transactionReportingService.postedCashFlowByCurrency(eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(Map.of("VND", new CurrencyCashFlow(
                        "VND", new BigDecimal("3000000"), 1, new BigDecimal("1200000"), 3)));
        when(budgetService.list(userId, java.time.YearMonth.of(2026, 8))).thenReturn(List.of(budget(BudgetStatus.WARNING)));
        when(savingGoalService.list(userId)).thenReturn(List.of(goal(SavingGoalStatus.ACTIVE), goal(SavingGoalStatus.COMPLETED)));
        when(transactionService.listRecent(userId)).thenReturn(List.of());

        DashboardResponse report = service.getDashboard(userId, "2026-08");

        assertThat(report.period()).isEqualTo(java.time.YearMonth.of(2026, 8));
        assertThat(report.activeWalletCount()).isEqualTo(2);
        assertThat(report.activeJarCount()).isEqualTo(1);
        assertThat(report.budgetAlertCount()).isEqualTo(1);
        assertThat(report.openSavingGoalCount()).isEqualTo(1);
        assertThat(report.currencySummaries()).extracting(CurrencyDashboardSummary::currency)
                .containsExactlyInAnyOrder("VND", "USD");
        assertThat(summary(report, "VND")).satisfies(total -> {
            assertThat(total.walletBalance()).isEqualByComparingTo("2000000");
            assertThat(total.allocatedToJars()).isEqualByComparingTo("800000");
            assertThat(total.availableToAllocate()).isEqualByComparingTo("1200000");
            assertThat(total.monthlyIncome()).isEqualByComparingTo("3000000");
            assertThat(total.monthlyExpense()).isEqualByComparingTo("1200000");
            assertThat(total.monthlyNet()).isEqualByComparingTo("1800000");
        });
        assertThat(summary(report, "USD").walletBalance()).isEqualByComparingTo("100");
        assertThat(summary(report, "USD").monthlyIncome()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private CurrencyDashboardSummary summary(DashboardResponse report, String currency) {
        return report.currencySummaries().stream()
                .filter(item -> item.currency().equals(currency))
                .findFirst()
                .orElseThrow();
    }

    private WalletResponse wallet(String name, String currency, String balance) {
        return new WalletResponse(UUID.randomUUID(), name, WalletType.CASH, currency, new BigDecimal(balance), false, Instant.now(), Instant.now());
    }

    private MoneyJarResponse jar(String name, String currency, String allocation) {
        return new MoneyJarResponse(UUID.randomUUID(), name, currency, new BigDecimal(allocation), null, null, null, false, Instant.now(), Instant.now());
    }

    private BudgetResponse budget(BudgetStatus status) {
        return new BudgetResponse(UUID.randomUUID(), UUID.randomUUID(), "Food", "utensils", "#0F8F72", LocalDate.of(2026, 8, 1),
                new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("1000000"), BigDecimal.ZERO, "VND", 80, status, Instant.now(), Instant.now());
    }

    private SavingGoalResponse goal(SavingGoalStatus status) {
        return new SavingGoalResponse(UUID.randomUUID(), UUID.randomUUID(), "Emergency", "VND", "Emergency fund",
                new BigDecimal("5000000"), BigDecimal.ZERO, new BigDecimal("5000000"), BigDecimal.ZERO, null, null, status, Instant.now(), Instant.now());
    }
}
