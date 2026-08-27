package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SplitBillResponse(
        UUID id,
        UUID expenseTransactionId,
        String name,
        BigDecimal totalAmount,
        BigDecimal payerShareAmount,
        BigDecimal reimbursableAmount,
        BigDecimal settledAmount,
        BigDecimal outstandingAmount,
        String currency,
        SplitBillStatus status,
        String description,
        String notes,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt,
        List<SplitBillParticipantResponse> participants,
        List<SplitBillPaymentResponse> payments) {

    static SplitBillResponse from(SplitBill bill, List<SplitBillPayment> payments) {
        List<SplitBillParticipantResponse> participants = bill.getParticipants().stream()
                .map(SplitBillParticipantResponse::from)
                .toList();
        BigDecimal reimbursable = bill.getTotalAmount().subtract(bill.getPayerShareAmount());
        BigDecimal settled = participants.stream()
                .map(SplitBillParticipantResponse::settledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SplitBillResponse(
                bill.getId(),
                bill.getExpenseTransactionId(),
                bill.getName(),
                bill.getTotalAmount(),
                bill.getPayerShareAmount(),
                reimbursable,
                settled,
                reimbursable.subtract(settled),
                bill.getCurrency(),
                bill.getStatus(),
                bill.getDescription(),
                bill.getNotes(),
                bill.getOccurredAt(),
                bill.getCreatedAt(),
                bill.getUpdatedAt(),
                participants,
                payments.stream().map(SplitBillPaymentResponse::from).toList());
    }
}
