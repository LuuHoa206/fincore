package com.luuhoa.fincore.monthlyreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.reporting.CurrencyDashboardSummary;
import com.luuhoa.fincore.reporting.DashboardResponse;
import com.luuhoa.fincore.reporting.DashboardService;
import com.luuhoa.fincore.reporting.FinancialInsightService;
import com.luuhoa.fincore.reporting.FinancialInsight;
import com.luuhoa.fincore.reporting.FinancialInsightSeverity;
import com.luuhoa.fincore.reporting.MonthlyFinancialInsightsResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MonthlyReviewServiceTest {

    @Mock private MonthlyReviewRepository monthlyReviewRepository;
    @Mock private UserAccountRepository userRepository;
    @Mock private DashboardService dashboardService;
    @Mock private FinancialInsightService financialInsightService;
    @Mock private AuditLogService auditLogService;

    private MonthlyReviewService service;

    @BeforeEach
    void setUp() {
        service = new MonthlyReviewService(monthlyReviewRepository, userRepository, dashboardService, financialInsightService, auditLogService);
    }

    @Test
    void returnsDerivedFactsWithoutCreatingAnEmptyReview() {
        UUID userId = UUID.randomUUID();
        stubFinancialFacts(userId, "2026-08");
        when(monthlyReviewRepository.findByUserIdAndPeriodStart(userId, LocalDate.of(2026, 8, 1))).thenReturn(Optional.empty());

        MonthlyReviewResponse response = service.get(userId, "2026-08");

        assertThat(response.reviewed()).isFalse();
        assertThat(response.currencySummaries()).hasSize(1);
        assertThat(response.insights()).hasSize(1);
    }

    @Test
    void savesReflectionWithoutChangingTheDerivedFinancialFacts() {
        UUID userId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UserAccount user = user(userId);
        MonthlyReview review = new MonthlyReview(user, LocalDate.of(2026, 8, 1), "Da giam chi an ngoai.", "Theo doi ngan sach an uong.");
        ReflectionTestUtils.setField(review, "id", reviewId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(monthlyReviewRepository.findByUserIdAndPeriodStart(userId, LocalDate.of(2026, 8, 1)))
                .thenReturn(Optional.empty(), Optional.of(review));
        when(monthlyReviewRepository.saveAndFlush(any(MonthlyReview.class))).thenReturn(review);
        stubFinancialFacts(userId, "2026-08");

        MonthlyReviewResponse response = service.save(userId, "2026-08", new SaveMonthlyReviewRequest(
                "Da giam chi an ngoai.", "Theo doi ngan sach an uong."));

        assertThat(response.reviewed()).isTrue();
        assertThat(response.reflection()).isEqualTo("Da giam chi an ngoai.");
        verify(auditLogService).record(eq(user), eq("MONTHLY_REVIEW_SAVED"), eq("MONTHLY_REVIEW"), eq(reviewId), anyMap());
    }

    @Test
    void rejectsAReviewWithoutEitherTextField() {
        UUID userId = UUID.randomUUID();
        UserAccount user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.save(userId, "2026-08", new SaveMonthlyReviewRequest(" ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reflection or a focus");
    }

    private void stubFinancialFacts(UUID userId, String period) {
        DashboardResponse dashboard = new DashboardResponse(
                YearMonth.parse(period), "Asia/Ho_Chi_Minh",
                List.of(new CurrencyDashboardSummary("VND", new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("1000000"),
                        new BigDecimal("2000000"), 2, new BigDecimal("500000"), 1, new BigDecimal("1500000"))),
                1, 0, 0, 0, List.of());
        when(dashboardService.getDashboard(userId, period)).thenReturn(dashboard);
        when(financialInsightService.summarize(userId, period)).thenReturn(new MonthlyFinancialInsightsResponse(
                YearMonth.parse(period), "Asia/Ho_Chi_Minh", List.of(new FinancialInsight(
                        "cash-flow-positive-VND", FinancialInsightSeverity.SUCCESS,
                        "Dong tien tich cuc", "Thang nay con du.", "VND", new BigDecimal("1500000")))));
    }

    private UserAccount user(UUID userId) {
        UserAccount user = new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
