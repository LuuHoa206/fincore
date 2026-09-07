package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.budget.BudgetResponse;
import com.luuhoa.fincore.budget.BudgetService;
import com.luuhoa.fincore.budget.BudgetStatus;
import com.luuhoa.fincore.savinggoal.SavingGoalResponse;
import com.luuhoa.fincore.savinggoal.SavingGoalService;
import com.luuhoa.fincore.savinggoal.SavingGoalStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces transparent, read-only monthly observations from existing financial data.
 * This service deliberately does not change transactions or make predictions.
 */
@Service
public class FinancialInsightService {

    private static final int MAX_INSIGHTS = 6;

    private final DashboardService dashboardService;
    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;

    public FinancialInsightService(
            DashboardService dashboardService,
            BudgetService budgetService,
            SavingGoalService savingGoalService) {
        this.dashboardService = dashboardService;
        this.budgetService = budgetService;
        this.savingGoalService = savingGoalService;
    }

    @Transactional(readOnly = true)
    public MonthlyFinancialInsightsResponse summarize(UUID userId, String requestedPeriod) {
        DashboardResponse dashboard = dashboardService.getDashboard(userId, requestedPeriod);
        List<InsightCandidate> candidates = new ArrayList<>();

        dashboard.currencySummaries().forEach(summary -> addCashFlowInsight(candidates, summary));
        budgetService.list(userId, dashboard.period()).forEach(budget -> addBudgetInsight(candidates, budget));
        addSavingGoalInsight(candidates, savingGoalService.list(userId));

        if (candidates.isEmpty()) {
            candidates.add(new InsightCandidate(100, new FinancialInsight(
                    "no-activity",
                    FinancialInsightSeverity.INFO,
                    "Chưa có dữ liệu giao dịch trong tháng",
                    "Hãy ghi nhận các khoản thu và chi để hệ thống có thể tổng hợp tình hình tài chính của bạn.",
                    null,
                    null)));
        }

        List<FinancialInsight> insights = candidates.stream()
                .sorted(Comparator.comparingInt(InsightCandidate::priority)
                        .thenComparing(candidate -> candidate.insight().key()))
                .limit(MAX_INSIGHTS)
                .map(InsightCandidate::insight)
                .toList();
        return new MonthlyFinancialInsightsResponse(dashboard.period(), dashboard.timeZone(), insights);
    }

    private void addCashFlowInsight(List<InsightCandidate> candidates, CurrencyDashboardSummary summary) {
        if (summary.monthlyIncome().signum() == 0 && summary.monthlyExpense().signum() == 0) {
            return;
        }

        BigDecimal net = summary.monthlyNet();
        if (net.signum() < 0) {
            candidates.add(new InsightCandidate(30, new FinancialInsight(
                    "cash-flow-negative-" + summary.currency(),
                    FinancialInsightSeverity.WARNING,
                    "Chi tiêu đang cao hơn thu nhập",
                    "Trong tháng này, khoản chi vượt khoản thu " + formatAmount(net.abs(), summary.currency()) + ".",
                    summary.currency(),
                    net.abs())));
            return;
        }

        if (net.signum() == 0) {
            candidates.add(new InsightCandidate(75, new FinancialInsight(
                    "cash-flow-balanced-" + summary.currency(),
                    FinancialInsightSeverity.INFO,
                    "Thu và chi tháng đang cân bằng",
                    "Tổng thu và tổng chi bằng nhau trong tháng này.",
                    summary.currency(),
                    BigDecimal.ZERO)));
            return;
        }

        candidates.add(new InsightCandidate(70, new FinancialInsight(
                "cash-flow-positive-" + summary.currency(),
                FinancialInsightSeverity.SUCCESS,
                "Dòng tiền tháng đang tích cực",
                "Sau khi trừ chi tiêu, bạn còn " + formatAmount(net, summary.currency()) + " trong tháng này.",
                summary.currency(),
                net)));
    }

    private void addBudgetInsight(List<InsightCandidate> candidates, BudgetResponse budget) {
        if (budget.status() == BudgetStatus.EXCEEDED) {
            candidates.add(new InsightCandidate(10, new FinancialInsight(
                    "budget-exceeded-" + budget.id(),
                    FinancialInsightSeverity.DANGER,
                    "Ngân sách " + budget.categoryName() + " đã vượt giới hạn",
                    "Bạn đã chi " + formatAmount(budget.spentAmount(), budget.currency()) + " trên ngân sách "
                            + formatAmount(budget.limitAmount(), budget.currency()) + ".",
                    budget.currency(),
                    budget.spentAmount())));
        } else if (budget.status() == BudgetStatus.WARNING) {
            candidates.add(new InsightCandidate(20, new FinancialInsight(
                    "budget-warning-" + budget.id(),
                    FinancialInsightSeverity.WARNING,
                    "Ngân sách " + budget.categoryName() + " cần chú ý",
                    "Bạn đã sử dụng " + budget.usagePercentage().stripTrailingZeros().toPlainString() + "% ngân sách tháng này.",
                    budget.currency(),
                    budget.spentAmount())));
        }
    }

    private void addSavingGoalInsight(List<InsightCandidate> candidates, List<SavingGoalResponse> goals) {
        goals.stream()
                .filter(goal -> goal.status() == SavingGoalStatus.ACTIVE || goal.status() == SavingGoalStatus.COMPLETED)
                .max(Comparator.comparing(SavingGoalResponse::progressPercentage))
                .ifPresent(goal -> {
                    if (goal.status() == SavingGoalStatus.COMPLETED) {
                        candidates.add(new InsightCandidate(40, new FinancialInsight(
                                "saving-goal-completed-" + goal.id(),
                                FinancialInsightSeverity.SUCCESS,
                                "Bạn đã hoàn thành mục tiêu " + goal.name(),
                                "Hũ " + goal.jarName() + " đã đạt " + formatAmount(goal.currentAmount(), goal.currency()) + ".",
                                goal.currency(),
                                goal.currentAmount())));
                    } else {
                        candidates.add(new InsightCandidate(80, new FinancialInsight(
                                "saving-goal-progress-" + goal.id(),
                                FinancialInsightSeverity.INFO,
                                "Tiến độ mục tiêu " + goal.name(),
                                "Bạn đã đạt " + goal.progressPercentage().stripTrailingZeros().toPlainString() + "% mục tiêu tiết kiệm này.",
                                goal.currency(),
                                goal.currentAmount())));
                    }
                });
    }

    private String formatAmount(BigDecimal amount, String currency) {
        return amount.stripTrailingZeros().toPlainString() + " " + currency;
    }

    private record InsightCandidate(int priority, FinancialInsight insight) {
    }
}
