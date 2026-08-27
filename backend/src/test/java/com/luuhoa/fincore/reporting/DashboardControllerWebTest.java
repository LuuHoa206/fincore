package com.luuhoa.fincore.reporting;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.config.SecurityConfig;
import com.luuhoa.fincore.shared.api.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DashboardControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private FinancialInsightService financialInsightService;

    @Test
    void returnsMonthlyInsightsOnlyForTheAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(financialInsightService.summarize(userId, "2026-08")).thenReturn(new MonthlyFinancialInsightsResponse(
                YearMonth.of(2026, 8),
                "Asia/Ho_Chi_Minh",
                List.of(new FinancialInsight(
                        "cash-flow-positive-VND",
                        FinancialInsightSeverity.SUCCESS,
                        "Dòng tiền tháng đang tích cực",
                        "Sau khi trừ chi tiêu, bạn còn 50000 VND trong tháng này.",
                        "VND",
                        new BigDecimal("50000")))));

        mockMvc.perform(get("/api/v1/reports/dashboard/insights")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .queryParam("period", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2026-08"))
                .andExpect(jsonPath("$.insights[0].severity").value("SUCCESS"))
                .andExpect(jsonPath("$.insights[0].amount").value(50000));

        verify(financialInsightService).summarize(eq(userId), eq("2026-08"));
    }

    @Test
    void protectsMonthlyInsightsLikeEveryOtherFinancialEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard/insights"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(financialInsightService);
    }
}
