package com.luuhoa.fincore.recurring;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;

public enum RecurringFrequency {
    DAILY,
    WEEKLY,
    MONTHLY;

    public Instant nextAfter(Instant scheduledAt, Instant referenceTime, ZoneId zoneId, int scheduleDay) {
        ZonedDateTime candidate = scheduledAt.atZone(zoneId);
        do {
            candidate = switch (this) {
                case DAILY -> candidate.plusDays(1);
                case WEEKLY -> candidate.plusWeeks(1);
                case MONTHLY -> nextMonthOnScheduleDay(candidate, scheduleDay);
            };
        } while (!candidate.toInstant().isAfter(referenceTime));
        return candidate.toInstant();
    }

    private ZonedDateTime nextMonthOnScheduleDay(ZonedDateTime current, int scheduleDay) {
        YearMonth nextMonth = YearMonth.from(current).plusMonths(1);
        LocalDate nextDate = nextMonth.atDay(Math.min(scheduleDay, nextMonth.lengthOfMonth()));
        return ZonedDateTime.of(nextDate, current.toLocalTime(), current.getZone());
    }
}
