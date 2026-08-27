package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordSplitBillPaymentRequest(
        @NotNull UUID walletId,
        @NotNull UUID incomeCategoryId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @Size(max = 4000) String notes,
        @NotNull Instant occurredAt) {
}
