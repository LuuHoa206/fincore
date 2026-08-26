package com.luuhoa.fincore.savinggoal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.moneyjar.MoneyJar;

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
@Table(name = "saving_goals")
public class SavingGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jar_id", nullable = false)
    private MoneyJar jar;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SavingGoalStatus status = SavingGoalStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected SavingGoal() {
    }

    public SavingGoal(UserAccount user, MoneyJar jar, String name, BigDecimal targetAmount, LocalDate targetDate) {
        this.user = user;
        this.jar = jar;
        this.name = name;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
    }

    public UUID getId() { return id; }
    public MoneyJar getJar() { return jar; }
    public String getName() { return name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public LocalDate getTargetDate() { return targetDate; }
    public SavingGoalStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateDetails(String name, BigDecimal targetAmount, LocalDate targetDate) {
        if (name != null) this.name = name;
        if (targetAmount != null) this.targetAmount = targetAmount;
        if (targetDate != null) this.targetDate = targetDate;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(SavingGoalStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
