package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_transaction_id")
    private FinancialTransaction reversedTransaction;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FinancialTransaction() {
    }

    public FinancialTransaction(
            UserAccount user,
            TransactionType transactionType,
            BigDecimal amount,
            String currency,
            String description,
            String notes,
            Instant occurredAt,
            String idempotencyKey) {
        this.user = user;
        this.transactionType = transactionType;
        this.status = TransactionStatus.POSTED;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.notes = notes;
        this.occurredAt = occurredAt;
        this.postedAt = Instant.now();
        this.idempotencyKey = idempotencyKey;
    }

    public static FinancialTransaction reversalOf(FinancialTransaction original, UserAccount user) {
        FinancialTransaction reversal = new FinancialTransaction(
                user,
                TransactionType.REVERSAL,
                original.amount,
                original.currency,
                "Reversal: " + original.description,
                original.notes,
                Instant.now(),
                null);
        reversal.reversedTransaction = original;
        return reversal;
    }

    public UUID getId() { return id; }
    public TransactionType getTransactionType() { return transactionType; }
    public TransactionStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getNotes() { return notes; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPostedAt() { return postedAt; }
    public FinancialTransaction getReversedTransaction() { return reversedTransaction; }

    public void markReversed() {
        this.status = TransactionStatus.REVERSED;
    }
}
