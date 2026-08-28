package com.luuhoa.fincore.financialcalendar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.transaction.TransactionType;

/** A single, non-mutating event rendered on the personal finance calendar. */
public record FinancialCalendarEntry(
        String key,
        UUID sourceId,
        FinancialCalendarEntryKind kind,
        Instant occurredAt,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        String title,
        String categoryName,
        String walletName,
        boolean autoRecord) {
}
