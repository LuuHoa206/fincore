package com.luuhoa.fincore.monthlyreview;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.config.SecurityConfig;
import com.luuhoa.fincore.shared.api.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MonthlyReviewController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MonthlyReviewControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MonthlyReviewService monthlyReviewService;

    @Test
    void returnsTheRequestedMonthlyReviewForTheAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(monthlyReviewService.get(userId, "2026-08")).thenReturn(new MonthlyReviewResponse(
                java.time.YearMonth.of(2026, 8), "Asia/Ho_Chi_Minh", List.of(), List.of(), false, null, null, null, null));

        mockMvc.perform(get("/api/v1/monthly-reviews").param("period", "2026-08")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2026-08"))
                .andExpect(jsonPath("$.reviewed").value(false));

        verify(monthlyReviewService).get(userId, "2026-08");
    }

    @Test
    void validatesReviewLengthBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/v1/monthly-reviews")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reflection\":\"" + "x".repeat(1501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(monthlyReviewService);
    }
}
