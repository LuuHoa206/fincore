package com.luuhoa.fincore.financialcalendar;

import java.time.YearMonth;
import java.util.List;

public record FinancialCalendarResponse(YearMonth period, String timeZone, List<FinancialCalendarDay> days) {
}
