package com.luuhoa.fincore.financialcalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.UserResponse;
import com.luuhoa.fincore.recurring.RecurringRuleResponse;
import com.luuhoa.fincore.recurring.RecurringRuleService;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes recorded transactions and future recurring occurrences for one
 * calendar month. The returned scheduled entries are projections only.
 */
@Service
public class FinancialCalendarService {

    private final AuthService authService;
    private final TransactionService transactionService;
    private final RecurringRuleService recurringRuleService;

    public FinancialCalendarService(
            AuthService authService,
            TransactionService transactionService,
            RecurringRuleService recurringRuleService) {
        this.authService = authService;
        this.transactionService = transactionService;
        this.recurringRuleService = recurringRuleService;
    }

    @Transactional(readOnly = true)
    public FinancialCalendarResponse get(UUID userId, String requestedPeriod) {
        return get(userId, requestedPeriod, Instant.now());
    }

    FinancialCalendarResponse get(UUID userId, String requestedPeriod, Instant now) {
        UserResponse user = authService.currentUser(userId);
        ZoneId zoneId = ZoneId.of(user.timeZone());
        YearMonth period = resolvePeriod(requestedPeriod, zoneId);
        Instant fromTime = period.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant toTime = period.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();
        LinkedHashMap<LocalDate, List<FinancialCalendarEntry>> entriesByDate = new LinkedHashMap<>();
        for (int day = 1; day <= period.lengthOfMonth(); day++) {
            entriesByDate.put(period.atDay(day), new ArrayList<>());
        }

        transactionService.listPostedInRange(userId, fromTime, toTime).forEach(transaction ->
                entriesByDate.get(transaction.occurredAt().atZone(zoneId).toLocalDate()).add(actualEntry(transaction)));
        recurringRuleService.list(userId).stream()
                .filter(RecurringRuleResponse::enabled)
                .flatMap(rule -> scheduledEntries(rule, fromTime, toTime, now, zoneId).stream())
                .forEach(entry -> entriesByDate.get(entry.occurredAt().atZone(zoneId).toLocalDate()).add(entry));

        return new FinancialCalendarResponse(
                period,
                user.timeZone(),
                entriesByDate.entrySet().stream()
                        .map(entry -> new FinancialCalendarDay(
                                entry.getKey(),
                                entry.getValue().stream()
                                        .sorted(Comparator.comparing(FinancialCalendarEntry::occurredAt)
                                                .thenComparing(FinancialCalendarEntry::kind)
                                                .thenComparing(FinancialCalendarEntry::title))
                                        .toList()))
                        .toList());
    }

    private List<FinancialCalendarEntry> scheduledEntries(
            RecurringRuleResponse rule,
            Instant fromTime,
            Instant toTime,
            Instant now,
            ZoneId zoneId) {
        Instant scheduledAt = firstOnOrAfter(rule, fromTime, zoneId);
        List<FinancialCalendarEntry> entries = new ArrayList<>();
        while (scheduledAt.isBefore(toTime)) {
            if (scheduledAt.isAfter(now)) {
                entries.add(new FinancialCalendarEntry(
                        "scheduled-" + rule.id() + "-" + scheduledAt,
                        rule.id(),
                        FinancialCalendarEntryKind.SCHEDULED,
                        scheduledAt,
                        rule.transactionType(),
                        rule.amount(),
                        rule.currency(),
                        rule.name(),
                        rule.categoryName(),
                        rule.walletName(),
                        rule.autoRecord()));
            }
            scheduledAt = rule.frequency().nextAfter(scheduledAt, scheduledAt, zoneId,
                    rule.scheduleDay());
        }
        return entries;
    }

    private Instant firstOnOrAfter(RecurringRuleResponse rule, Instant fromTime, ZoneId zoneId) {
        Instant scheduledAt = rule.nextRunAt();
        return scheduledAt.isBefore(fromTime)
                ? rule.frequency().nextAfter(scheduledAt, fromTime, zoneId,
                        rule.scheduleDay())
                : scheduledAt;
    }

    private FinancialCalendarEntry actualEntry(TransactionResponse transaction) {
        return new FinancialCalendarEntry(
                "actual-" + transaction.id(),
                transaction.id(),
                FinancialCalendarEntryKind.ACTUAL,
                transaction.occurredAt(),
                transaction.transactionType(),
                transaction.amount(),
                transaction.currency(),
                transaction.description(),
                transaction.categoryName(),
                transaction.walletName(),
                false);
    }

    private YearMonth resolvePeriod(String requestedPeriod, ZoneId zoneId) {
        if (requestedPeriod == null || requestedPeriod.isBlank()) return YearMonth.now(zoneId);
        try {
            return YearMonth.parse(requestedPeriod.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("period must use the YYYY-MM format");
        }
    }
}
