package com.luuhoa.fincore.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A read-only, explainable recommendation. Applying it still requires the user
 * to explicitly create a budget through the normal budget flow.
 */
public record BudgetLimitSuggestionResponse(
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        LocalDate periodStart,
        String currency,
        int historyMonths,
        BigDecimal historicalExpenseTotal,
        BigDecimal suggestedLimit,
        String reason) {
}
