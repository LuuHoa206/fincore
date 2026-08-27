package com.luuhoa.fincore.recurring;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.transaction.TransactionType;
import com.luuhoa.fincore.wallet.Wallet;

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
@Table(name = "recurring_transaction_rules")
public class RecurringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Column(name = "schedule_day", nullable = false)
    private int scheduleDay;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "auto_record", nullable = false)
    private boolean autoRecord;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "apply_allocation_rule", nullable = false)
    private boolean applyAllocationRule;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected RecurringRule() {
    }

    public RecurringRule(
            UserAccount user,
            Wallet wallet,
            Category category,
            String name,
            TransactionType transactionType,
            BigDecimal amount,
            String currency,
            String description,
            String notes,
            RecurringFrequency frequency,
            Instant nextRunAt,
            boolean autoRecord,
            boolean enabled,
            boolean applyAllocationRule) {
        this.user = user;
        this.wallet = wallet;
        this.category = category;
        this.name = name;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.notes = notes;
        this.frequency = frequency;
        this.nextRunAt = nextRunAt;
        this.scheduleDay = nextRunAt.atZone(ZoneId.of(user.getTimeZone())).getDayOfMonth();
        this.autoRecord = autoRecord;
        this.enabled = enabled;
        this.applyAllocationRule = applyAllocationRule;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public Wallet getWallet() { return wallet; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public TransactionType getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getNotes() { return notes; }
    public RecurringFrequency getFrequency() { return frequency; }
    public int getScheduleDay() { return scheduleDay; }
    public Instant getNextRunAt() { return nextRunAt; }
    public boolean isAutoRecord() { return autoRecord; }
    public boolean isEnabled() { return enabled; }
    public boolean isApplyAllocationRule() { return applyAllocationRule; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isDueAt(Instant now) {
        return enabled && !nextRunAt.isAfter(now);
    }

    public void update(
            Wallet wallet,
            Category category,
            String name,
            TransactionType transactionType,
            BigDecimal amount,
            String currency,
            String description,
            String notes,
            RecurringFrequency frequency,
            Instant nextRunAt,
            boolean autoRecord,
            boolean enabled,
            boolean applyAllocationRule) {
        this.wallet = wallet;
        this.category = category;
        this.name = name;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.notes = notes;
        this.frequency = frequency;
        this.nextRunAt = nextRunAt;
        this.scheduleDay = nextRunAt.atZone(ZoneId.of(user.getTimeZone())).getDayOfMonth();
        this.autoRecord = autoRecord;
        this.enabled = enabled;
        this.applyAllocationRule = applyAllocationRule;
        this.updatedAt = Instant.now();
    }

    public void advanceNextRunAt(Instant referenceTime) {
        this.nextRunAt = frequency.nextAfter(nextRunAt, referenceTime, ZoneId.of(user.getTimeZone()), scheduleDay);
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }
}
