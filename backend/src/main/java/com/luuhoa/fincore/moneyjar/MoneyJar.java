package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.shared.api.ConflictException;

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
@Table(name = "money_jars")
public class MoneyJar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "allocated_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedBalance = BigDecimal.ZERO.setScale(4);

    @Column(name = "spending_limit", precision = 19, scale = 4)
    private BigDecimal spendingLimit;

    @Column(length = 20)
    private String color;

    @Column(length = 50)
    private String icon;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected MoneyJar() {
    }

    public MoneyJar(
            UserAccount user,
            String name,
            String currency,
            BigDecimal spendingLimit,
            String color,
            String icon,
            boolean allowNegative) {
        this.user = user;
        this.name = name;
        this.currency = currency;
        this.spendingLimit = spendingLimit;
        this.color = color;
        this.icon = icon;
        this.allowNegative = allowNegative;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAllocatedBalance() {
        return allocatedBalance;
    }

    public BigDecimal getSpendingLimit() {
        return spendingLimit;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isAllowNegative() {
        return allowNegative;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            String name,
            BigDecimal spendingLimit,
            String color,
            String icon,
            Boolean allowNegative) {
        if (name != null) {
            this.name = name;
        }
        if (spendingLimit != null) {
            this.spendingLimit = spendingLimit;
        }
        if (color != null) {
            this.color = color;
        }
        if (icon != null) {
            this.icon = icon;
        }
        if (allowNegative != null) {
            this.allowNegative = allowNegative;
        }
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.archived = true;
        this.updatedAt = Instant.now();
    }

    public void applyAllocation(BigDecimal signedAmount) {
        BigDecimal nextBalance = allocatedBalance.add(signedAmount);
        if (!allowNegative && nextBalance.signum() < 0) {
            throw new ConflictException("INSUFFICIENT_JAR_BALANCE", "This jar does not have enough allocated balance");
        }
        this.allocatedBalance = nextBalance;
        this.updatedAt = Instant.now();
    }
}
