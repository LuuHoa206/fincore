package com.luuhoa.fincore.recurring;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringRuleScheduler {

    private final RecurringRuleService recurringRuleService;

    public RecurringRuleScheduler(RecurringRuleService recurringRuleService) {
        this.recurringRuleService = recurringRuleService;
    }

    @Scheduled(fixedDelayString = "${app.recurring.fixed-delay:PT5M}")
    public void recordDueRules() {
        recurringRuleService.processDueAutoRecords(Instant.now());
    }
}
