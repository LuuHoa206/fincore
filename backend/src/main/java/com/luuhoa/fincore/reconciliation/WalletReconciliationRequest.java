package com.luuhoa.fincore.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record WalletReconciliationRequest(
        @NotNull UUID walletId,
        @NotNull LocalDate statementDate,
        @NotNull @Digits(integer = 15, fraction = 4) BigDecimal statementBalance) {
}
