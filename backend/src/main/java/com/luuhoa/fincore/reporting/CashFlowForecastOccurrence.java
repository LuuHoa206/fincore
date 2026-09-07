package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.transaction.TransactionType;

public record CashFlowForecastOccurrence(
        UUID recurringRuleId,
        String ruleName,
        String walletName,
        String categoryName,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        Instant scheduledAt) {
}
