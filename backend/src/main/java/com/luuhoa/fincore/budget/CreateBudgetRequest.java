package com.luuhoa.fincore.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBudgetRequest(
        @NotNull UUID categoryId,
        @NotNull LocalDate periodStart,
        @NotNull @DecimalMin(value = "0.01") BigDecimal limitAmount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Min(1) @Max(100) Integer warningThreshold) {
}
