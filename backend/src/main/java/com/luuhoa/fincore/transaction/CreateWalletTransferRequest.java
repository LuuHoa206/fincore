package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A transfer is intentionally separate from income and expense: it only moves
 * money between two wallets owned by the same user.
 */
public record CreateWalletTransferRequest(
        @NotNull UUID sourceWalletId,
        @NotNull UUID destinationWalletId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 4000) String notes,
        @NotNull Instant occurredAt) {
}
