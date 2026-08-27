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

    public DashboardController(DashboardService dashboardService, FinancialInsightService financialInsightService) {
        this.dashboardService = dashboardService;
        this.financialInsightService = financialInsightService;
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
}
