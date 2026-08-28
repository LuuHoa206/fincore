package com.luuhoa.fincore.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WalletReconciliationPreview(
        UUID walletId,
        String walletName,
        String currency,
        LocalDate statementDate,
        String timeZone,
        BigDecimal ledgerBalance,
        BigDecimal statementBalance,
        BigDecimal difference,
        long transactionCount,
        ReconciliationStatus status) {
}
