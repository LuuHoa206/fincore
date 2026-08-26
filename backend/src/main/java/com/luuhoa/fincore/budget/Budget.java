package com.luuhoa.fincore.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.identity.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "limit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal limitAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "warning_threshold", nullable = false)
    private int warningThreshold;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected Budget() {
    }

    public Budget(
            UserAccount user,
            Category category,
            LocalDate periodStart,
            BigDecimal limitAmount,
            String currency,
            int warningThreshold) {
        this.user = user;
        this.category = category;
        this.periodStart = periodStart;
        this.limitAmount = limitAmount;
        this.currency = currency;
        this.warningThreshold = warningThreshold;
    }

    public UUID getId() { return id; }
    public Category getCategory() { return category; }
    public LocalDate getPeriodStart() { return periodStart; }
    public BigDecimal getLimitAmount() { return limitAmount; }
    public String getCurrency() { return currency; }
    public int getWarningThreshold() { return warningThreshold; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updatePlan(BigDecimal limitAmount, Integer warningThreshold) {
        if (limitAmount != null) {
            this.limitAmount = limitAmount;
        }
        if (warningThreshold != null) {
            this.warningThreshold = warningThreshold;
        }
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.archived = true;
        this.updatedAt = Instant.now();
    }
}
