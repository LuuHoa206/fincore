package com.luuhoa.fincore.reporting;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final FinancialInsightService financialInsightService;
    private final ExpenseAnomalyDetectionService expenseAnomalyDetectionService;
    private final CashFlowForecastService cashFlowForecastService;
    private final CashFlowTrendService cashFlowTrendService;

    public DashboardController(
            DashboardService dashboardService,
            FinancialInsightService financialInsightService,
            ExpenseAnomalyDetectionService expenseAnomalyDetectionService,
            CashFlowForecastService cashFlowForecastService,
            CashFlowTrendService cashFlowTrendService) {
        this.dashboardService = dashboardService;
        this.financialInsightService = financialInsightService;
        this.expenseAnomalyDetectionService = expenseAnomalyDetectionService;
        this.cashFlowForecastService = cashFlowForecastService;
        this.cashFlowTrendService = cashFlowTrendService;
    }

    @GetMapping
    DashboardResponse getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String period) {
        return dashboardService.getDashboard(UUID.fromString(jwt.getSubject()), period);
    }

    @GetMapping("/insights")
    MonthlyFinancialInsightsResponse getMonthlyInsights(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String period) {
        return financialInsightService.summarize(UUID.fromString(jwt.getSubject()), period);
    }

    @GetMapping("/unusual-expenses")
    UnusualExpensesResponse getUnusualExpenses(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String period) {
        return expenseAnomalyDetectionService.findUnusualExpenses(UUID.fromString(jwt.getSubject()), period);
    }

    @GetMapping("/cash-flow-forecast")
    CashFlowForecastResponse getCashFlowForecast(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer days) {
        return cashFlowForecastService.forecast(UUID.fromString(jwt.getSubject()), days);
    }

    @GetMapping("/cash-flow-trend")
    CashFlowTrendResponse getCashFlowTrend(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer months) {
        return cashFlowTrendService.trend(UUID.fromString(jwt.getSubject()), months);
    }
}
