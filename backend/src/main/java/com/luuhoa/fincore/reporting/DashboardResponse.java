package com.luuhoa.fincore.reporting;

import java.time.YearMonth;
import java.util.List;

import com.luuhoa.fincore.transaction.TransactionResponse;

public record DashboardResponse(
        YearMonth period,
        String timeZone,
        List<CurrencyDashboardSummary> currencySummaries,
        int activeWalletCount,
        int activeJarCount,
        int budgetAlertCount,
        int openSavingGoalCount,
        List<TransactionResponse> recentTransactions) {
}
