package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSplitBillRequest(
        @NotNull UUID walletId,
        @NotNull UUID expenseCategoryId,
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal totalAmount,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal payerShareAmount,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 4000) String notes,
        @NotNull Instant occurredAt,
        @NotEmpty List<@Valid SplitBillParticipantRequest> participants) {
}
