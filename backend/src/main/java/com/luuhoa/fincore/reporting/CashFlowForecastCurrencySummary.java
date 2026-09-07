package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;

public record CashFlowForecastCurrencySummary(
        String currency,
        BigDecimal projectedIncome,
        BigDecimal projectedExpense,
        BigDecimal projectedNet) {
}
