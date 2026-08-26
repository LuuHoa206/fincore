package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private FinancialTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false, length = 20)
    private AccountKind accountKind;

    @Column(name = "account_label", length = 120)
    private String accountLabel;

    @Column(name = "signed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal signedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LedgerEntry() {
    }

    private LedgerEntry(
            FinancialTransaction transaction,
            Wallet wallet,
            AccountKind accountKind,
            String accountLabel,
            BigDecimal signedAmount,
            String currency) {
        this.transaction = transaction;
        this.wallet = wallet;
        this.accountKind = accountKind;
        this.accountLabel = accountLabel;
        this.signedAmount = signedAmount;
        this.currency = currency;
    }

    public static LedgerEntry walletEntry(FinancialTransaction transaction, Wallet wallet, BigDecimal signedAmount) {
        return new LedgerEntry(transaction, wallet, AccountKind.WALLET, null, signedAmount, wallet.getCurrency());
    }

    public static LedgerEntry externalEntry(FinancialTransaction transaction, String label, BigDecimal signedAmount, String currency) {
        return new LedgerEntry(transaction, null, AccountKind.EXTERNAL, label, signedAmount, currency);
    }

    public Wallet getWallet() { return wallet; }
    public BigDecimal getSignedAmount() { return signedAmount; }
    public AccountKind getAccountKind() { return accountKind; }
}
