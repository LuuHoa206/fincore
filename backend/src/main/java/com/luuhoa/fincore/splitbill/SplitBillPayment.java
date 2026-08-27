package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "split_bill_payments")
public class SplitBillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private SplitBillParticipant participant;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SplitBillPayment() {
    }

    public SplitBillPayment(SplitBillParticipant participant, UUID transactionId, BigDecimal amount, Instant occurredAt) {
        this.participant = participant;
        this.transactionId = transactionId;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public SplitBillParticipant getParticipant() { return participant; }
    public UUID getTransactionId() { return transactionId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
