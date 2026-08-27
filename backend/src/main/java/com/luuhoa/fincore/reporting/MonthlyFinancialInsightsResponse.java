package com.luuhoa.fincore.reporting;

import java.time.YearMonth;
import java.util.List;

public record MonthlyFinancialInsightsResponse(
        YearMonth period,
        String timeZone,
        List<FinancialInsight> insights) {
}
