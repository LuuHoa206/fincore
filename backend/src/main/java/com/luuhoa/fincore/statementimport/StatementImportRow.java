package com.luuhoa.fincore.statementimport;

import java.math.BigDecimal;
import java.time.Instant;

import com.luuhoa.fincore.transaction.TransactionType;

public record StatementImportRow(
        int rowNumber,
        StatementImportRowStatus status,
        TransactionType transactionType,
        BigDecimal amount,
        String description,
        String notes,
        Instant occurredAt,
        String reason) {
}
