package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.TransactionReportingService;
import com.luuhoa.fincore.transaction.TransactionReportingService.CategoryCurrency;
import com.luuhoa.fincore.transaction.TransactionReportingService.ExpenseCandidate;
import com.luuhoa.fincore.transaction.TransactionReportingService.HistoricalExpenseBaseline;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Highlights unusually large posted expenses using a transparent historical rule.
 * It never modifies transactions and does not label a transaction as fraud.
 */
@Service
public class ExpenseAnomalyDetectionService {

    private static final int HISTORY_MONTHS = 3;
    private static final long MINIMUM_HISTORY_COUNT = 3;
    private static final BigDecimal MEDIUM_MULTIPLIER = new BigDecimal("2.5");
    private static final BigDecimal HIGH_MULTIPLIER = new BigDecimal("5");
    private static final int MAX_FINDINGS = 10;

    private final UserAccountRepository userRepository;
    private final TransactionReportingService transactionReportingService;

    public ExpenseAnomalyDetectionService(
            UserAccountRepository userRepository,
            TransactionReportingService transactionReportingService) {
        this.userRepository = userRepository;
        this.transactionReportingService = transactionReportingService;
    }

    @Transactional(readOnly = true)
    public UnusualExpensesResponse findUnusualExpenses(UUID userId, String requestedPeriod) {
        UserAccount user = requireUser(userId);
        ZoneId zoneId = ZoneId.of(user.getTimeZone());
        YearMonth period = resolvePeriod(requestedPeriod, zoneId);
        Instant periodStart = period.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant periodEnd = period.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();
        Instant historyStart = period.minusMonths(HISTORY_MONTHS).atDay(1).atStartOfDay(zoneId).toInstant();

        Map<CategoryCurrency, HistoricalExpenseBaseline> baselines = transactionReportingService
                .postedExpenseBaselines(userId, historyStart, periodStart);
        List<UnusualExpense> findings = transactionReportingService.postedExpenseCandidates(userId, periodStart, periodEnd).stream()
                .map(candidate -> observation(candidate, baselines.get(new CategoryCurrency(candidate.categoryId(), candidate.currency()))))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(UnusualExpense::severity).reversed()
                        .thenComparing(UnusualExpense::multipleOfAverage, Comparator.reverseOrder())
                        .thenComparing(UnusualExpense::occurredAt, Comparator.reverseOrder()))
                .limit(MAX_FINDINGS)
                .toList();

        return new UnusualExpensesResponse(period, user.getTimeZone(), findings);
    }

    private java.util.Optional<UnusualExpense> observation(
            ExpenseCandidate candidate,
            HistoricalExpenseBaseline baseline) {
        if (baseline == null || baseline.transactionCount() < MINIMUM_HISTORY_COUNT) {
            return java.util.Optional.empty();
        }

        BigDecimal average = baseline.totalAmount()
                .divide(BigDecimal.valueOf(baseline.transactionCount()), 4, RoundingMode.HALF_UP);
        if (average.signum() <= 0) {
            return java.util.Optional.empty();
        }

        BigDecimal multiple = candidate.amount().divide(average, 2, RoundingMode.HALF_UP);
        if (multiple.compareTo(MEDIUM_MULTIPLIER) < 0) {
            return java.util.Optional.empty();
        }

        ExpenseAnomalySeverity severity = multiple.compareTo(HIGH_MULTIPLIER) >= 0
                ? ExpenseAnomalySeverity.HIGH
                : ExpenseAnomalySeverity.MEDIUM;
        return java.util.Optional.of(new UnusualExpense(
                candidate.transactionId(),
                candidate.description(),
                candidate.categoryName(),
                candidate.currency(),
                candidate.amount(),
                average,
                baseline.transactionCount(),
                multiple,
                severity,
                "Khoản chi này cao gấp " + multiple.stripTrailingZeros().toPlainString()
                        + " lần mức trung bình của " + baseline.transactionCount()
                        + " khoản chi trước đó cùng danh mục.",
                candidate.occurredAt()));
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private YearMonth resolvePeriod(String requestedPeriod, ZoneId zoneId) {
        if (requestedPeriod == null || requestedPeriod.isBlank()) {
            return YearMonth.now(zoneId);
        }
        try {
            return YearMonth.parse(requestedPeriod.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("period must use the YYYY-MM format");
        }
    }
}
