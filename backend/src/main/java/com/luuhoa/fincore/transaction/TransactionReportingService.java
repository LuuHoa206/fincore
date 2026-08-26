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
}
