package com.luuhoa.fincore.splitbill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SplitBillRepository extends JpaRepository<SplitBill, UUID> {

    @Query("select distinct bill from SplitBill bill left join fetch bill.participants where bill.user.id = :userId order by bill.occurredAt desc")
    List<SplitBill> findAllByUserIdWithParticipants(@Param("userId") UUID userId);

    @Query("select distinct bill from SplitBill bill left join fetch bill.participants where bill.id = :billId and bill.user.id = :userId")
    Optional<SplitBill> findOwnedWithParticipants(@Param("billId") UUID billId, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct bill from SplitBill bill left join fetch bill.participants where bill.id = :billId and bill.user.id = :userId")
    Optional<SplitBill> findOwnedForUpdateWithParticipants(@Param("billId") UUID billId, @Param("userId") UUID userId);

    @Query("select distinct bill from SplitBill bill left join fetch bill.participants where bill.user.id = :userId and bill.expenseTransactionId = :transactionId")
    Optional<SplitBill> findByUserIdAndExpenseTransactionIdWithParticipants(@Param("userId") UUID userId, @Param("transactionId") UUID transactionId);

    boolean existsByUserIdAndExpenseTransactionIdAndStatusNot(UUID userId, UUID expenseTransactionId, SplitBillStatus status);
}
