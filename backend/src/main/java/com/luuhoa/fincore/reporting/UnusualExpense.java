package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A read-only expense observation. It is not a fraud decision or an automatic action.
 */
public record UnusualExpense(
        UUID transactionId,
        String description,
        String categoryName,
        String currency,
        BigDecimal amount,
        BigDecimal historicalAverageAmount,
        long historicalTransactionCount,
        BigDecimal multipleOfAverage,
        ExpenseAnomalySeverity severity,
        String reason,
        Instant occurredAt) {
}
