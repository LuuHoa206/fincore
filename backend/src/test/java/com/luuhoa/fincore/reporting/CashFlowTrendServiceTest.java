package com.luuhoa.fincore.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.transaction.TransactionReportingService;
import com.luuhoa.fincore.transaction.TransactionReportingService.CurrencyCashFlow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashFlowTrendServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private UserAccountRepository userRepository;
    @Mock private TransactionReportingService transactionReportingService;

    private CashFlowTrendService service;

    @BeforeEach
    void setUp() {
        service = new CashFlowTrendService(userRepository, transactionReportingService);
    }

    @Test
    void returnsChronologicalPerCurrencySeriesAndKeepsMissingMonthsAtZero() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new UserAccount(
                "owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh")));
        when(transactionReportingService.postedCashFlowByCurrency(eq(USER_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(Map.of("VND", flow("VND", "1000000", "400000")))
                .thenReturn(Map.of("USD", flow("USD", "200", "50")))
                .thenReturn(Map.of("VND", flow("VND", "3000000", "1200000"), "USD", flow("USD", "0", "30")));

        CashFlowTrendResponse response = service.trend(USER_ID, 3, Instant.parse("2026-08-15T12:00:00Z"));

        assertThat(response.months()).isEqualTo(3);
        assertThat(response.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(response.currencySeries()).extracting(CashFlowTrendCurrency::currency)
                .containsExactly("USD", "VND");

        CashFlowTrendCurrency vnd = series(response, "VND");
        assertThat(vnd.points()).extracting(MonthlyCashFlowTrendPoint::period)
                .containsExactly(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));
        assertThat(vnd.points()).extracting(MonthlyCashFlowTrendPoint::net)
                .containsExactly(new BigDecimal("600000"), BigDecimal.ZERO, new BigDecimal("1800000"));

        CashFlowTrendCurrency usd = series(response, "USD");
        assertThat(usd.points()).extracting(MonthlyCashFlowTrendPoint::income)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("200"), BigDecimal.ZERO);
        assertThat(usd.points()).extracting(MonthlyCashFlowTrendPoint::expense)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("50"), new BigDecimal("30"));

        verify(transactionReportingService, times(3))
                .postedCashFlowByCurrency(eq(USER_ID), any(Instant.class), any(Instant.class));
    }

    @Test
    void rejectsAnUnboundedTrendRequestBeforeAccessingUserData() {
        assertThatThrownBy(() -> service.trend(USER_ID, 2, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Trend months must be between 3 and 12");
        assertThatThrownBy(() -> service.trend(USER_ID, 13, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Trend months must be between 3 and 12");

        verifyNoInteractions(userRepository, transactionReportingService);
    }

    private CashFlowTrendCurrency series(CashFlowTrendResponse response, String currency) {
        return response.currencySeries().stream()
                .filter(item -> item.currency().equals(currency))
                .findFirst()
                .orElseThrow();
    }

    private CurrencyCashFlow flow(String currency, String income, String expense) {
        return new CurrencyCashFlow(currency, new BigDecimal(income), 1, new BigDecimal(expense), 1);
    }
}
