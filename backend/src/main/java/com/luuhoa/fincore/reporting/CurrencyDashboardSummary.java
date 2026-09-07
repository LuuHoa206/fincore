package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;

public record CurrencyDashboardSummary(
        String currency,
        BigDecimal walletBalance,
        BigDecimal allocatedToJars,
        BigDecimal availableToAllocate,
        BigDecimal monthlyIncome,
        long incomeTransactionCount,
        BigDecimal monthlyExpense,
        long expenseTransactionCount,
        BigDecimal monthlyNet) {
}
