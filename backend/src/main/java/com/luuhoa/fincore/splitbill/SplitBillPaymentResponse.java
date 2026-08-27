package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SplitBillPaymentResponse(
        UUID id,
        UUID participantId,
        UUID transactionId,
        BigDecimal amount,
        Instant occurredAt) {

    static SplitBillPaymentResponse from(SplitBillPayment payment) {
        return new SplitBillPaymentResponse(
                payment.getId(),
                payment.getParticipant().getId(),
                payment.getTransactionId(),
                payment.getAmount(),
                payment.getOccurredAt());
    }
}
