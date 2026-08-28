package com.luuhoa.fincore.financialcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.UserResponse;
import com.luuhoa.fincore.recurring.RecurringFrequency;
import com.luuhoa.fincore.recurring.RecurringRuleResponse;
import com.luuhoa.fincore.recurring.RecurringRuleService;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.transaction.TransactionStatus;
import com.luuhoa.fincore.transaction.TransactionType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialCalendarServiceTest {

    @Mock private AuthService authService;
    @Mock private TransactionService transactionService;
    @Mock private RecurringRuleService recurringRuleService;

    private FinancialCalendarService service;

    @BeforeEach
    void setUp() {
        service = new FinancialCalendarService(authService, transactionService, recurringRuleService);
    }

    @Test
    void combinesPostedTransactionsWithOnlyFutureScheduledOccurrences() {
        UUID userId = UUID.randomUUID();
        when(authService.currentUser(userId)).thenReturn(user(userId));
        Instant postedAt = Instant.parse("2026-08-12T02:00:00Z");
        when(transactionService.listPostedInRange(userId,
                Instant.parse("2026-07-31T17:00:00Z"), Instant.parse("2026-08-31T17:00:00Z")))
                .thenReturn(List.of(transaction(postedAt)));
        when(recurringRuleService.list(userId)).thenReturn(List.of(
                recurring(Instant.parse("2026-08-20T02:00:00Z")),
                recurring(Instant.parse("2026-08-10T02:00:00Z"))));

        FinancialCalendarResponse response = service.get(userId, "2026-08", Instant.parse("2026-08-15T00:00:00Z"));

        assertThat(response.period().toString()).isEqualTo("2026-08");
        assertThat(response.days()).hasSize(31);
        assertThat(response.days().get(11).entries()).singleElement()
                .extracting(FinancialCalendarEntry::kind).isEqualTo(FinancialCalendarEntryKind.ACTUAL);
        assertThat(response.days().get(19).entries()).singleElement()
                .extracting(FinancialCalendarEntry::kind).isEqualTo(FinancialCalendarEntryKind.SCHEDULED);
        assertThat(response.days().stream().flatMap(day -> day.entries().stream()))
                .noneMatch(entry -> entry.kind() == FinancialCalendarEntryKind.SCHEDULED
                        && entry.occurredAt().equals(Instant.parse("2026-08-10T02:00:00Z")));
    }

    @Test
    void preservesMonthlyScheduleDayWhenProjectingAcrossShorterMonths() {
        UUID userId = UUID.randomUUID();
        when(authService.currentUser(userId)).thenReturn(user(userId));
        when(transactionService.listPostedInRange(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(recurringRuleService.list(userId)).thenReturn(List.of(recurring(
                Instant.parse("2026-01-31T02:00:00Z"), RecurringFrequency.MONTHLY, 31)));

        FinancialCalendarResponse response = service.get(userId, "2026-03", Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(response.days().get(30).entries()).singleElement()
                .extracting(FinancialCalendarEntry::kind).isEqualTo(FinancialCalendarEntryKind.SCHEDULED);
    }

    private UserResponse user(UUID userId) {
        return new UserResponse(userId, "owner@example.com", "Owner", "VND", "Asia/Ho_Chi_Minh", Set.of("ROLE_USER"), Instant.now());
    }

    private TransactionResponse transaction(Instant occurredAt) {
        return new TransactionResponse(UUID.randomUUID(), UUID.randomUUID(), "Ngân hàng", null, null,
                UUID.randomUUID(), "Ăn uống", "utensils", "#0F8F72", TransactionType.EXPENSE,
                TransactionStatus.POSTED, new BigDecimal("55000"), "VND", "Ăn trưa", null,
                occurredAt, occurredAt, null);
    }

    private RecurringRuleResponse recurring(Instant nextRunAt) {
        return recurring(nextRunAt, RecurringFrequency.MONTHLY, 20);
    }

    private RecurringRuleResponse recurring(Instant nextRunAt, RecurringFrequency frequency, int scheduleDay) {
        return new RecurringRuleResponse(UUID.randomUUID(), "Tiền thuê nhà", UUID.randomUUID(), "Ngân hàng",
                UUID.randomUUID(), "Nhà ở", TransactionType.EXPENSE, new BigDecimal("5000000"), "VND",
                "Thanh toán tiền thuê", null, frequency, scheduleDay, nextRunAt, true, true, false,
                nextRunAt.minusSeconds(60), nextRunAt.minusSeconds(30));
    }
}
