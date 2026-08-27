package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.shared.api.ConflictException;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "split_bill_participants")
public class SplitBillParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "split_bill_id", nullable = false)
    private SplitBill bill;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 160)
    private String contact;

    @Column(name = "owed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal owedAmount;

    @Column(name = "settled_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal settledAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SplitBillParticipantStatus status = SplitBillParticipantStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected SplitBillParticipant() {
    }

    SplitBillParticipant(SplitBill bill, String name, String contact, BigDecimal owedAmount) {
        this.bill = bill;
        this.name = name;
        this.contact = contact;
        this.owedAmount = owedAmount;
    }

    public void recordPayment(BigDecimal amount) {
        if (amount.compareTo(getOutstandingAmount()) > 0) {
            throw new ConflictException("SPLIT_BILL_PAYMENT_EXCEEDS_OUTSTANDING", "Payment is greater than the participant's outstanding amount");
        }
        settledAmount = settledAmount.add(amount);
        status = settledAmount.compareTo(owedAmount) >= 0
                ? SplitBillParticipantStatus.PAID
                : SplitBillParticipantStatus.PARTIALLY_PAID;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public SplitBill getBill() { return bill; }
    public String getName() { return name; }
    public String getContact() { return contact; }
    public BigDecimal getOwedAmount() { return owedAmount; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public BigDecimal getOutstandingAmount() { return owedAmount.subtract(settledAmount); }
    public SplitBillParticipantStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
