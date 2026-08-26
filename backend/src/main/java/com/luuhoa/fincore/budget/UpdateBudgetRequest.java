package com.luuhoa.fincore.budget;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateBudgetRequest(
        @DecimalMin(value = "0.01") BigDecimal limitAmount,
        @Min(1) @Max(100) Integer warningThreshold) {
}
