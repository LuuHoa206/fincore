package com.luuhoa.fincore.reporting;

import java.time.Instant;
import java.util.List;

public record CashFlowForecastResponse(
        Instant from,
        Instant toExclusive,
        int days,
        String timeZone,
        List<CashFlowForecastCurrencySummary> currencySummaries,
        List<CashFlowForecastOccurrence> upcomingOccurrences) {
}
