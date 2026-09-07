package com.luuhoa.fincore.financialcalendar;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@WebMvcTest(FinancialCalendarController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class FinancialCalendarControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FinancialCalendarService financialCalendarService;

    @Test
    void returnsTheRequestedCalendarForTheAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(financialCalendarService.get(userId, "2026-08"))
                .thenReturn(new FinancialCalendarResponse(YearMonth.of(2026, 8), "Asia/Ho_Chi_Minh", List.of()));

        mockMvc.perform(get("/api/v1/financial-calendar").param("period", "2026-08")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2026-08"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"));

        verify(financialCalendarService).get(eq(userId), eq("2026-08"));
    }
}
