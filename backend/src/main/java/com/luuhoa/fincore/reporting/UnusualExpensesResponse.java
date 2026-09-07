package com.luuhoa.fincore.reporting;

import java.time.YearMonth;
import java.util.List;

public record UnusualExpensesResponse(
        YearMonth period,
        String timeZone,
        List<UnusualExpense> findings) {
}
