package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only transaction aggregates exposed to other business modules.
 */
@Service
public class TransactionReportingService {

    private final FinancialTransactionRepository transactionRepository;

    public TransactionReportingService(FinancialTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> postedExpensesByCategory(
            UUID userId,
            List<UUID> categoryIds,
            String currency,
            Instant periodStart,
            Instant periodEnd) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return transactionRepository.sumAmountsByCategory(
                userId,
                categoryIds,
                currency,
                TransactionType.EXPENSE,
                TransactionStatus.POSTED,
                periodStart,
                periodEnd).stream()
                .collect(Collectors.toMap(
                        FinancialTransactionRepository.CategoryExpenseTotal::getCategoryId,
                        total -> total.getTotalAmount() == null ? BigDecimal.ZERO : total.getTotalAmount(),
                        BigDecimal::add));
    }

    @Transactional(readOnly = true)
    public Map<String, CurrencyCashFlow> postedCashFlowByCurrency(UUID userId, Instant periodStart, Instant periodEnd) {
        Map<String, CurrencyCashFlow> totals = new java.util.LinkedHashMap<>();
        transactionRepository.summarizeAmountsByCurrencyAndType(
                userId,
                TransactionStatus.POSTED,
                List.of(TransactionType.INCOME, TransactionType.EXPENSE),
                periodStart,
                periodEnd).forEach(total -> {
                    CurrencyCashFlow current = totals.getOrDefault(total.getCurrency(), CurrencyCashFlow.empty(total.getCurrency()));
                    totals.put(total.getCurrency(), total.getTransactionType() == TransactionType.INCOME
                            ? current.withIncome(total.getTotalAmount(), total.getTransactionCount())
                            : current.withExpense(total.getTotalAmount(), total.getTransactionCount()));
                });
        return Map.copyOf(totals);
    }

    public record CurrencyCashFlow(
            String currency,
            BigDecimal incomeAmount,
            long incomeCount,
            BigDecimal expenseAmount,
            long expenseCount) {

        public static CurrencyCashFlow empty(String currency) {
            return new CurrencyCashFlow(currency, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0);
        }

        CurrencyCashFlow withIncome(BigDecimal amount, long count) {
            return new CurrencyCashFlow(currency, safeAmount(amount), count, expenseAmount, expenseCount);
        }

        CurrencyCashFlow withExpense(BigDecimal amount, long count) {
            return new CurrencyCashFlow(currency, incomeAmount, incomeCount, safeAmount(amount), count);
        }

        private static BigDecimal safeAmount(BigDecimal amount) {
            return amount == null ? BigDecimal.ZERO : amount;
        }
    }
}
