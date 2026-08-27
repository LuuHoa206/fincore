package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;

public record FinancialInsight(
        String key,
        FinancialInsightSeverity severity,
        String title,
        String message,
        String currency,
        BigDecimal amount) {
}
