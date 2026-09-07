package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.util.UUID;

public record SplitBillParticipantResponse(
        UUID id,
        String name,
        String contact,
        BigDecimal owedAmount,
        BigDecimal settledAmount,
        BigDecimal outstandingAmount,
        SplitBillParticipantStatus status) {

    static SplitBillParticipantResponse from(SplitBillParticipant participant) {
        return new SplitBillParticipantResponse(
                participant.getId(),
                participant.getName(),
                participant.getContact(),
                participant.getOwedAmount(),
                participant.getSettledAmount(),
                participant.getOutstandingAmount(),
                participant.getStatus());
    }
}
