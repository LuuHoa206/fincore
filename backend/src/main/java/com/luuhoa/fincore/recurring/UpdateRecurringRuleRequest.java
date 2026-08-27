package com.luuhoa.fincore.recurring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.transaction.TransactionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRecurringRuleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull UUID walletId,
        @NotNull UUID categoryId,
        @NotNull TransactionType transactionType,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 4000) String notes,
        @NotNull RecurringFrequency frequency,
        @NotNull Instant nextRunAt,
        boolean autoRecord,
        boolean enabled,
        boolean applyAllocationRule) {
}
