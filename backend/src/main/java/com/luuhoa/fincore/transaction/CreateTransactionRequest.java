package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTransactionRequest(
        @NotNull UUID walletId,
        @NotNull UUID categoryId,
        @NotNull TransactionType transactionType,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 4000) String notes,
        @NotNull Instant occurredAt,
        Boolean applyAllocationRule) {
}
