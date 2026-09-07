package com.luuhoa.fincore.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.transaction.TransactionReportingService;
import com.luuhoa.fincore.transaction.TransactionReportingService.CategoryCurrency;
import com.luuhoa.fincore.transaction.TransactionReportingService.ExpenseCandidate;
import com.luuhoa.fincore.transaction.TransactionReportingService.HistoricalExpenseBaseline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseAnomalyDetectionServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock private UserAccountRepository userRepository;
    @Mock private TransactionReportingService transactionReportingService;
    @Mock private UserAccount user;

    private ExpenseAnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseAnomalyDetectionService(userRepository, transactionReportingService);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getTimeZone()).thenReturn("Asia/Ho_Chi_Minh");
    }

    @Test
    void flagsOnlyExpensesWithEnoughHistoryAndAtLeastTwoPointFiveTimesTheAverage() {
        Instant historyStart = Instant.parse("2026-05-31T17:00:00Z");
        Instant periodStart = Instant.parse("2026-08-31T17:00:00Z");
        Instant periodEnd = Instant.parse("2026-09-30T17:00:00Z");
        CategoryCurrency key = new CategoryCurrency(CATEGORY_ID, "VND");
        when(transactionReportingService.postedExpenseBaselines(USER_ID, historyStart, periodStart))
                .thenReturn(Map.of(key, new HistoricalExpenseBaseline(new BigDecimal("600000"), 3)));
        when(transactionReportingService.postedExpenseCandidates(USER_ID, periodStart, periodEnd)).thenReturn(List.of(
                candidate("1500000", "Bảo dưỡng xe"),
                candidate("400000", "Đổ xăng")));

        UnusualExpensesResponse response = service.findUnusualExpenses(USER_ID, "2026-09");

        assertThat(response.period()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(response.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.description()).isEqualTo("Bảo dưỡng xe");
            assertThat(finding.historicalAverageAmount()).isEqualByComparingTo("200000");
            assertThat(finding.multipleOfAverage()).isEqualByComparingTo("7.5");
            assertThat(finding.severity()).isEqualTo(ExpenseAnomalySeverity.HIGH);
            assertThat(finding.reason()).contains("3 khoản chi trước đó cùng danh mục");
        });

        verify(transactionReportingService).postedExpenseBaselines(eq(USER_ID), eq(historyStart), eq(periodStart));
        verify(transactionReportingService).postedExpenseCandidates(eq(USER_ID), eq(periodStart), eq(periodEnd));
    }

    @Test
    void doesNotFlagATransactionWhenThereIsNotEnoughHistoricalEvidence() {
        Instant historyStart = Instant.parse("2026-05-31T17:00:00Z");
        Instant periodStart = Instant.parse("2026-08-31T17:00:00Z");
        Instant periodEnd = Instant.parse("2026-09-30T17:00:00Z");
        when(transactionReportingService.postedExpenseBaselines(USER_ID, historyStart, periodStart))
                .thenReturn(Map.of(new CategoryCurrency(CATEGORY_ID, "VND"), new HistoricalExpenseBaseline(new BigDecimal("100000"), 2)));
        when(transactionReportingService.postedExpenseCandidates(USER_ID, periodStart, periodEnd))
                .thenReturn(List.of(candidate("1000000", "Mua thiết bị")));

        UnusualExpensesResponse response = service.findUnusualExpenses(USER_ID, "2026-09");

        assertThat(response.findings()).isEmpty();
    }

    private ExpenseCandidate candidate(String amount, String description) {
        return new ExpenseCandidate(
                UUID.randomUUID(),
                CATEGORY_ID,
                "Di chuyển",
                "VND",
                new BigDecimal(amount),
                description,
                Instant.parse("2026-09-15T03:00:00Z"));
    }
}
