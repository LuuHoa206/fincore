package com.luuhoa.fincore.savinggoal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSavingGoalRequest(
        @NotNull UUID jarId,
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotNull @DecimalMin(value = "0.01") BigDecimal targetAmount,
        @FutureOrPresent LocalDate targetDate) {
}
