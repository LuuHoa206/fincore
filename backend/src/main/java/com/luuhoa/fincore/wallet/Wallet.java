package com.luuhoa.fincore.wallet;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false, length = 30)
    private WalletType walletType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance = BigDecimal.ZERO.setScale(4);

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

    protected Wallet() {
    }

    public Wallet(UserAccount user, String name, WalletType walletType, String currency, boolean allowNegative) {
        this.user = user;
        this.name = name;
        this.walletType = walletType;
        this.currency = currency;
        this.allowNegative = allowNegative;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WalletType getWalletType() {
        return walletType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public boolean isAllowNegative() {
        return allowNegative;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(String name, WalletType walletType, Boolean allowNegative) {
        if (name != null) {
            this.name = name;
        }
        if (walletType != null) {
            this.walletType = walletType;
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
}
