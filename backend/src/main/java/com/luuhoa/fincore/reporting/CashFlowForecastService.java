package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.recurring.RecurringRule;
import com.luuhoa.fincore.recurring.RecurringRuleRepository;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.TransactionType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects only enabled recurring rules. It is intentionally deterministic and
 * does not create transactions or alter a rule's next scheduled time.
 */
@Service
public class CashFlowForecastService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int MAX_RETURNED_OCCURRENCES = 12;

    private final UserAccountRepository userRepository;
    private final RecurringRuleRepository recurringRuleRepository;

    public CashFlowForecastService(
            UserAccountRepository userRepository,
            RecurringRuleRepository recurringRuleRepository) {
        this.userRepository = userRepository;
        this.recurringRuleRepository = recurringRuleRepository;
    }

    @Transactional(readOnly = true)
    public CashFlowForecastResponse forecast(UUID userId, Integer requestedDays) {
        return forecast(userId, requestedDays, Instant.now());
    }

    CashFlowForecastResponse forecast(UUID userId, Integer requestedDays, Instant now) {
        int days = requestedDays == null ? DEFAULT_DAYS : requestedDays;
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("Forecast days must be between 7 and 90");
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        ZoneId zoneId = ZoneId.of(user.getTimeZone());
        Instant toExclusive = now.plus(days, ChronoUnit.DAYS);
        List<CashFlowForecastOccurrence> occurrences = recurringRuleRepository.findAllEnabledByUserIdWithDetails(userId).stream()
                .flatMap(rule -> occurrencesFor(rule, now, toExclusive, zoneId).stream())
                .sorted(Comparator.comparing(CashFlowForecastOccurrence::scheduledAt)
                        .thenComparing(CashFlowForecastOccurrence::ruleName))
                .toList();

        Map<String, CashFlowTotals> totalsByCurrency = new TreeMap<>();
        occurrences.forEach(occurrence -> totalsByCurrency
                .computeIfAbsent(occurrence.currency(), ignored -> new CashFlowTotals())
                .add(occurrence));
        List<CashFlowForecastCurrencySummary> summaries = totalsByCurrency.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();

        return new CashFlowForecastResponse(
                now,
                toExclusive,
                days,
                user.getTimeZone(),
                summaries,
                occurrences.stream().limit(MAX_RETURNED_OCCURRENCES).toList());
    }

    private List<CashFlowForecastOccurrence> occurrencesFor(
            RecurringRule rule,
            Instant from,
            Instant toExclusive,
            ZoneId zoneId) {
        Instant scheduledAt = firstOnOrAfter(rule, from, zoneId);
        List<CashFlowForecastOccurrence> occurrences = new ArrayList<>();
        while (scheduledAt.isBefore(toExclusive)) {
            occurrences.add(new CashFlowForecastOccurrence(
                    rule.getId(),
                    rule.getName(),
                    rule.getWallet().getName(),
                    rule.getCategory().getName(),
                    rule.getTransactionType(),
                    rule.getAmount(),
                    rule.getCurrency(),
                    scheduledAt));
            scheduledAt = rule.getFrequency().nextAfter(scheduledAt, scheduledAt, zoneId, rule.getScheduleDay());
        }
        return occurrences;
    }

    private Instant firstOnOrAfter(RecurringRule rule, Instant from, ZoneId zoneId) {
        Instant scheduledAt = rule.getNextRunAt();
        return scheduledAt.isBefore(from)
                ? rule.getFrequency().nextAfter(scheduledAt, from, zoneId, rule.getScheduleDay())
                : scheduledAt;
    }

    private static final class CashFlowTotals {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;

        void add(CashFlowForecastOccurrence occurrence) {
            if (occurrence.transactionType() == TransactionType.INCOME) {
                income = income.add(occurrence.amount());
            } else {
                expense = expense.add(occurrence.amount());
            }
        }

        CashFlowForecastCurrencySummary toResponse(String currency) {
            return new CashFlowForecastCurrencySummary(currency, income, expense, income.subtract(expense));
        }
    }
}
