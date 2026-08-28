package com.luuhoa.fincore.monthlyreview;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import com.luuhoa.fincore.reporting.CurrencyDashboardSummary;
import com.luuhoa.fincore.reporting.FinancialInsight;

public record MonthlyReviewResponse(
        YearMonth period,
        String timeZone,
        List<CurrencyDashboardSummary> currencySummaries,
        List<FinancialInsight> insights,
        boolean reviewed,
        String reflection,
        String nextMonthFocus,
        Instant reviewedAt,
        Instant updatedAt) {
}
