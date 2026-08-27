package com.luuhoa.fincore.splitbill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SplitBillPaymentRepository extends JpaRepository<SplitBillPayment, UUID> {

    @Query("select payment from SplitBillPayment payment join fetch payment.participant participant where participant.bill.id = :billId order by payment.occurredAt desc, payment.createdAt desc")
    List<SplitBillPayment> findAllByBillId(@Param("billId") UUID billId);

    Optional<SplitBillPayment> findByTransactionId(UUID transactionId);

    boolean existsByTransactionId(UUID transactionId);
}
