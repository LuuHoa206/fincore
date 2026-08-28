package com.luuhoa.fincore.monthlyreview;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.reporting.DashboardResponse;
import com.luuhoa.fincore.reporting.DashboardService;
import com.luuhoa.fincore.reporting.FinancialInsightService;
import com.luuhoa.fincore.reporting.MonthlyFinancialInsightsResponse;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists a user's reflection only; monetary summaries remain derived from reporting services. */
@Service
public class MonthlyReviewService {

    private final MonthlyReviewRepository monthlyReviewRepository;
    private final UserAccountRepository userRepository;
    private final DashboardService dashboardService;
    private final FinancialInsightService financialInsightService;
    private final AuditLogService auditLogService;

    public MonthlyReviewService(
            MonthlyReviewRepository monthlyReviewRepository,
            UserAccountRepository userRepository,
            DashboardService dashboardService,
            FinancialInsightService financialInsightService,
            AuditLogService auditLogService) {
        this.monthlyReviewRepository = monthlyReviewRepository;
        this.userRepository = userRepository;
        this.dashboardService = dashboardService;
        this.financialInsightService = financialInsightService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public MonthlyReviewResponse get(UUID userId, String requestedPeriod) {
        DashboardResponse dashboard = dashboardService.getDashboard(userId, requestedPeriod);
        MonthlyFinancialInsightsResponse insights = financialInsightService.summarize(userId, dashboard.period().toString());
        return monthlyReviewRepository.findByUserIdAndPeriodStart(userId, dashboard.period().atDay(1))
                .map(review -> response(dashboard, insights, review))
                .orElseGet(() -> response(dashboard, insights, null));
    }

    @Transactional
    public MonthlyReviewResponse save(UUID userId, String requestedPeriod, SaveMonthlyReviewRequest request) {
        UserAccount user = requireUser(userId);
        YearMonth period = resolvePeriod(requestedPeriod, user.getTimeZone());
        String reflection = normalize(request.reflection());
        String nextMonthFocus = normalize(request.nextMonthFocus());
        if (reflection == null && nextMonthFocus == null) {
            throw new IllegalArgumentException("Enter a reflection or a focus for next month before saving");
        }

        MonthlyReview review = monthlyReviewRepository.findByUserIdAndPeriodStart(userId, period.atDay(1))
                .map(existing -> {
                    existing.update(reflection, nextMonthFocus);
                    return existing;
                })
                .orElseGet(() -> new MonthlyReview(user, period.atDay(1), reflection, nextMonthFocus));
        MonthlyReview saved = monthlyReviewRepository.saveAndFlush(review);
        auditLogService.record(user, "MONTHLY_REVIEW_SAVED", "MONTHLY_REVIEW", saved.getId(), new LinkedHashMap<>(java.util.Map.of(
                "period", period.toString(),
                "hasReflection", reflection != null,
                "hasNextMonthFocus", nextMonthFocus != null)));
        return get(userId, period.toString());
    }

    private MonthlyReviewResponse response(DashboardResponse dashboard, MonthlyFinancialInsightsResponse insights, MonthlyReview review) {
        return new MonthlyReviewResponse(
                dashboard.period(),
                dashboard.timeZone(),
                dashboard.currencySummaries(),
                insights.insights(),
                review != null,
                review == null ? null : review.getReflection(),
                review == null ? null : review.getNextMonthFocus(),
                review == null ? null : review.getReviewedAt(),
                review == null ? null : review.getUpdatedAt());
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private YearMonth resolvePeriod(String requestedPeriod, String timeZone) {
        if (requestedPeriod == null || requestedPeriod.isBlank()) return YearMonth.now(java.time.ZoneId.of(timeZone));
        try {
            return YearMonth.parse(requestedPeriod.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("period must use the YYYY-MM format");
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
