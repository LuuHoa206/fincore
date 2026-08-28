package com.luuhoa.fincore.monthlyreview;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "monthly_reviews", uniqueConstraints = @UniqueConstraint(name = "uk_monthly_reviews_user_period", columnNames = { "user_id", "period_start" }))
public class MonthlyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(length = 1500)
    private String reflection;

    @Column(name = "next_month_focus", length = 500)
    private String nextMonthFocus;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected MonthlyReview() {
    }

    public MonthlyReview(UserAccount user, LocalDate periodStart, String reflection, String nextMonthFocus) {
        this.user = user;
        this.periodStart = periodStart;
        this.reflection = reflection;
        this.nextMonthFocus = nextMonthFocus;
    }

    public UUID getId() { return id; }
    public LocalDate getPeriodStart() { return periodStart; }
    public String getReflection() { return reflection; }
    public String getNextMonthFocus() { return nextMonthFocus; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String reflection, String nextMonthFocus) {
        this.reflection = reflection;
        this.nextMonthFocus = nextMonthFocus;
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
