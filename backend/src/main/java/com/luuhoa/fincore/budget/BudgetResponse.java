package com.luuhoa.fincore.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        LocalDate periodStart,
        BigDecimal limitAmount,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        BigDecimal usagePercentage,
        String currency,
        int warningThreshold,
        BudgetStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
