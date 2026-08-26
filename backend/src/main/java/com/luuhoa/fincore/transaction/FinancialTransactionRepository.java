package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

    List<FinancialTransaction> findTop100ByUserIdOrderByOccurredAtDesc(UUID userId);

    Optional<FinancialTransaction> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    boolean existsByReversedTransactionId(UUID originalTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from FinancialTransaction transaction where transaction.id = :transactionId and transaction.user.id = :userId")
    Optional<FinancialTransaction> findOwnedForUpdate(@Param("transactionId") UUID transactionId, @Param("userId") UUID userId);

    @Query("""
            select transaction.category.id as categoryId, coalesce(sum(transaction.amount), 0) as totalAmount
            from FinancialTransaction transaction
            where transaction.user.id = :userId
              and transaction.category.id in :categoryIds
              and transaction.currency = :currency
              and transaction.transactionType = :transactionType
              and transaction.status = :status
              and transaction.occurredAt >= :periodStart
              and transaction.occurredAt < :periodEnd
            group by transaction.category.id
            """)
    List<CategoryExpenseTotal> sumAmountsByCategory(
            @Param("userId") UUID userId,
            @Param("categoryIds") List<UUID> categoryIds,
            @Param("currency") String currency,
            @Param("transactionType") TransactionType transactionType,
            @Param("status") TransactionStatus status,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    interface CategoryExpenseTotal {
        UUID getCategoryId();

        BigDecimal getTotalAmount();
    }
}
