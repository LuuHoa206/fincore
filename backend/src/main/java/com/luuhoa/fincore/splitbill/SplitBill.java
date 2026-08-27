package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "split_bills")
public class SplitBill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "expense_transaction_id", nullable = false, unique = true)
    private UUID expenseTransactionId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "payer_share_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal payerShareAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SplitBillStatus status;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<SplitBillParticipant> participants = new ArrayList<>();

    protected SplitBill() {
    }

    public SplitBill(
            UserAccount user,
            UUID expenseTransactionId,
            String name,
            BigDecimal totalAmount,
            BigDecimal payerShareAmount,
            String currency,
            String description,
            String notes,
            Instant occurredAt) {
        this.user = user;
        this.expenseTransactionId = expenseTransactionId;
        this.name = name;
        this.totalAmount = totalAmount;
        this.payerShareAmount = payerShareAmount;
        this.currency = currency;
        this.status = SplitBillStatus.OPEN;
        this.description = description;
        this.notes = notes;
        this.occurredAt = occurredAt;
    }

    public void addParticipant(String name, String contact, BigDecimal owedAmount) {
        participants.add(new SplitBillParticipant(this, name, contact, owedAmount));
        touch();
    }

    public void refreshStatus() {
        BigDecimal settledTotal = participants.stream()
                .map(SplitBillParticipant::getSettledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivable = totalAmount.subtract(payerShareAmount);
        status = settledTotal.signum() == 0
                ? SplitBillStatus.OPEN
                : settledTotal.compareTo(receivable) >= 0 ? SplitBillStatus.SETTLED : SplitBillStatus.PARTIALLY_SETTLED;
        touch();
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public UUID getExpenseTransactionId() { return expenseTransactionId; }
    public String getName() { return name; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPayerShareAmount() { return payerShareAmount; }
    public String getCurrency() { return currency; }
    public SplitBillStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getNotes() { return notes; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<SplitBillParticipant> getParticipants() { return List.copyOf(participants); }

    private void touch() {
        updatedAt = Instant.now();
    }
}
