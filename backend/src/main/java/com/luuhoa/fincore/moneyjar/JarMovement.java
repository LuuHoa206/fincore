package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.transaction.FinancialTransaction;

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
@Table(name = "jar_movements")
public class JarMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jar_id", nullable = false)
    private MoneyJar jar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private FinancialTransaction transaction;

    @Column(name = "signed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal signedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 120)
    private JarMovementReason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected JarMovement() {
    }

    private JarMovement(MoneyJar jar, BigDecimal signedAmount, JarMovementReason reason) {
        this.jar = jar;
        this.signedAmount = signedAmount;
        this.currency = jar.getCurrency();
        this.reason = reason;
    }

    public static JarMovement allocation(MoneyJar jar, BigDecimal amount) {
        return new JarMovement(jar, amount, JarMovementReason.ALLOCATION);
    }

    public static JarMovement allocation(MoneyJar jar, FinancialTransaction transaction, BigDecimal amount) {
        JarMovement movement = allocation(jar, amount);
        movement.transaction = transaction;
        return movement;
    }

    public static JarMovement release(MoneyJar jar, BigDecimal amount) {
        return new JarMovement(jar, amount.negate(), JarMovementReason.RELEASE);
    }

    public static JarMovement transferOut(MoneyJar jar, BigDecimal amount) {
        return new JarMovement(jar, amount.negate(), JarMovementReason.TRANSFER_OUT);
    }

    public static JarMovement transferIn(MoneyJar jar, BigDecimal amount) {
        return new JarMovement(jar, amount, JarMovementReason.TRANSFER_IN);
    }
}
