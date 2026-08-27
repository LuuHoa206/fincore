package com.luuhoa.fincore.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class RecurringFrequencyTest {

    @Test
    void monthlyRuleKeepsLocalCalendarCadenceAndSkipsMissedPeriods() {
        Instant scheduledAt = Instant.parse("2026-01-31T02:00:00Z");
        Instant now = Instant.parse("2026-03-15T02:00:00Z");

        Instant nextRunAt = RecurringFrequency.MONTHLY.nextAfter(scheduledAt, now, ZoneId.of("Asia/Ho_Chi_Minh"), 31);

        assertThat(nextRunAt).isEqualTo(Instant.parse("2026-03-31T02:00:00Z"));
    }
}
