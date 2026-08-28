package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A confirmed statement difference. The transaction module recomputes the
 * difference while holding the financial write lock before it records anything.
 */
public record ReconciliationAdjustmentCommand(
        UUID walletId,
        LocalDate statementDate,
        BigDecimal statementBalance,
        Instant endExclusive,
        String reason) {
}
