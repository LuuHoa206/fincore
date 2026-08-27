package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.TransactionReportingService;
import com.luuhoa.fincore.transaction.TransactionReportingService.CurrencyCashFlow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only monthly cash-flow series built from posted transactions only.
 */
@Service
public class CashFlowTrendService {

    private static final int DEFAULT_MONTHS = 6;
    private static final int MIN_MONTHS = 3;
    private static final int MAX_MONTHS = 12;

    private final UserAccountRepository userRepository;
    private final TransactionReportingService transactionReportingService;

    public CashFlowTrendService(
            UserAccountRepository userRepository,
            TransactionReportingService transactionReportingService) {
        this.userRepository = userRepository;
        this.transactionReportingService = transactionReportingService;
    }

    @Transactional(readOnly = true)
    public CashFlowTrendResponse trend(UUID userId, Integer requestedMonths) {
        return trend(userId, requestedMonths, Instant.now());
    }

    CashFlowTrendResponse trend(UUID userId, Integer requestedMonths, Instant now) {
        int months = requestedMonths == null ? DEFAULT_MONTHS : requestedMonths;
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new IllegalArgumentException("Trend months must be between 3 and 12");
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        ZoneId zoneId = ZoneId.of(user.getTimeZone());
        YearMonth currentMonth = YearMonth.from(now.atZone(zoneId));
        List<YearMonth> periods = periods(currentMonth, months);
        List<Map<String, CurrencyCashFlow>> cashFlowByPeriod = periods.stream()
                .map(period -> cashFlowForPeriod(userId, period, zoneId))
                .toList();

        TreeSet<String> currencies = new TreeSet<>();
        cashFlowByPeriod.forEach(cashFlow -> currencies.addAll(cashFlow.keySet()));
        List<CashFlowTrendCurrency> series = currencies.stream()
                .map(currency -> new CashFlowTrendCurrency(currency, points(currency, periods, cashFlowByPeriod)))
                .toList();

        return new CashFlowTrendResponse(months, user.getTimeZone(), series);
    }

    private Map<String, CurrencyCashFlow> cashFlowForPeriod(UUID userId, YearMonth period, ZoneId zoneId) {
        Instant periodStart = period.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant periodEnd = period.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();
        return transactionReportingService.postedCashFlowByCurrency(userId, periodStart, periodEnd);
    }

    private List<YearMonth> periods(YearMonth currentMonth, int months) {
        List<YearMonth> periods = new ArrayList<>(months);
        YearMonth first = currentMonth.minusMonths(months - 1L);
        for (int index = 0; index < months; index++) {
            periods.add(first.plusMonths(index));
        }
        return List.copyOf(periods);
    }

    private List<MonthlyCashFlowTrendPoint> points(
            String currency,
            List<YearMonth> periods,
            List<Map<String, CurrencyCashFlow>> cashFlowByPeriod) {
        List<MonthlyCashFlowTrendPoint> points = new ArrayList<>(periods.size());
        for (int index = 0; index < periods.size(); index++) {
            CurrencyCashFlow cashFlow = cashFlowByPeriod.get(index)
                    .getOrDefault(currency, CurrencyCashFlow.empty(currency));
            BigDecimal income = cashFlow.incomeAmount();
            BigDecimal expense = cashFlow.expenseAmount();
            points.add(new MonthlyCashFlowTrendPoint(periods.get(index), income, expense, income.subtract(expense)));
        }
        return points.stream().sorted(Comparator.comparing(MonthlyCashFlowTrendPoint::period)).toList();
    }
}
