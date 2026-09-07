package com.luuhoa.fincore.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.budget.BudgetResponse;
import com.luuhoa.fincore.budget.BudgetService;
import com.luuhoa.fincore.budget.BudgetStatus;
import com.luuhoa.fincore.savinggoal.SavingGoalResponse;
import com.luuhoa.fincore.savinggoal.SavingGoalService;
import com.luuhoa.fincore.savinggoal.SavingGoalStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialInsightServiceTest {

    @Mock private DashboardService dashboardService;
    @Mock private BudgetService budgetService;
    @Mock private SavingGoalService savingGoalService;

    private FinancialInsightService service;

    @BeforeEach
    void setUp() {
        service = new FinancialInsightService(dashboardService, budgetService, savingGoalService);
    }

    @Test
    void prioritizesExceededBudgetBeforeCashFlowAndGoalProgress() {
        UUID userId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 8);
        when(dashboardService.getDashboard(userId, "2026-08")).thenReturn(dashboard(period,
                new CurrencyDashboardSummary("VND", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("100000"), 1, new BigDecimal("150000"), 2, new BigDecimal("-50000"))));
        when(budgetService.list(userId, period)).thenReturn(List.of(budget(BudgetStatus.EXCEEDED, "Ăn uống")));
        when(savingGoalService.list(userId)).thenReturn(List.of(goal(SavingGoalStatus.ACTIVE, "Quỹ khẩn cấp", "20")));

        MonthlyFinancialInsightsResponse response = service.summarize(userId, "2026-08");

        assertThat(response.period()).isEqualTo(period);
        assertThat(response.insights()).extracting(FinancialInsight::key)
                .containsExactly("budget-exceeded-" + budgetId(), "cash-flow-negative-VND", "saving-goal-progress-" + goalId());
        assertThat(response.insights().getFirst().severity()).isEqualTo(FinancialInsightSeverity.DANGER);
        assertThat(response.insights().get(1).amount()).isEqualByComparingTo("50000");
    }

    @Test
    void returnsAnActionableEmptyStateWhenTheMonthHasNoFinancialData() {
        UUID userId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 8);
        when(dashboardService.getDashboard(userId, null)).thenReturn(dashboard(period));
        when(budgetService.list(userId, period)).thenReturn(List.of());
        when(savingGoalService.list(userId)).thenReturn(List.of());

        MonthlyFinancialInsightsResponse response = service.summarize(userId, null);

        assertThat(response.insights()).singleElement().satisfies(insight -> {
            assertThat(insight.key()).isEqualTo("no-activity");
            assertThat(insight.severity()).isEqualTo(FinancialInsightSeverity.INFO);
        });
    }

    private DashboardResponse dashboard(YearMonth period, CurrencyDashboardSummary... summaries) {
        return new DashboardResponse(period, "Asia/Ho_Chi_Minh", List.of(summaries), 1, 0, 0, 0, List.of());
    }

    private BudgetResponse budget(BudgetStatus status, String categoryName) {
        return new BudgetResponse(budgetId(), UUID.randomUUID(), categoryName, "utensils", "#0F8F72", LocalDate.of(2026, 8, 1),
                new BigDecimal("100000"), new BigDecimal("120000"), new BigDecimal("-20000"), new BigDecimal("120"), "VND", 80,
                status, Instant.now(), Instant.now());
    }

    private SavingGoalResponse goal(SavingGoalStatus status, String name, String progress) {
        return new SavingGoalResponse(goalId(), UUID.randomUUID(), "Khẩn cấp", "VND", name,
                new BigDecimal("1000000"), new BigDecimal("200000"), new BigDecimal("800000"), new BigDecimal(progress), null,
                null, status, Instant.now(), Instant.now());
    }

    private UUID budgetId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private UUID goalId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000002");
    }
}
