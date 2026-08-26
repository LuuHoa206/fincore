package com.luuhoa.fincore.savinggoal;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;

public record UpdateSavingGoalRequest(
        @Size(min = 2, max = 120) String name,
        @DecimalMin(value = "0.01") BigDecimal targetAmount,
        @FutureOrPresent LocalDate targetDate) {
}
