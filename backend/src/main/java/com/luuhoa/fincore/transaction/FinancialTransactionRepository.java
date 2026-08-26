package com.luuhoa.fincore.transaction;

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
}
