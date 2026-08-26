package com.luuhoa.fincore.savinggoal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SavingGoalResponse(
        UUID id,
        UUID jarId,
        String jarName,
        String currency,
        String name,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        BigDecimal remainingAmount,
        BigDecimal progressPercentage,
        BigDecimal suggestedMonthlyContribution,
        LocalDate targetDate,
        SavingGoalStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
