package com.luuhoa.fincore.financialcalendar;

import java.time.LocalDate;
import java.util.List;

public record FinancialCalendarDay(LocalDate date, List<FinancialCalendarEntry> entries) {
}
