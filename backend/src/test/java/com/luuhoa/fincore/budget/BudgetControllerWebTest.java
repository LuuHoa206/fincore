package com.luuhoa.fincore.budget;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(BudgetController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BudgetControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BudgetService budgetService;
    @MockitoBean private BudgetLimitSuggestionService budgetLimitSuggestionService;

    @Test
    void rejectsUnauthenticatedBudgetSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/suggestions"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetLimitSuggestionService);
    }

    @Test
    void forwardsSuggestionQueryOnlyForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        BudgetLimitSuggestionResponse response = new BudgetLimitSuggestionResponse(
                UUID.randomUUID(), "Food", "utensils", "#0F8F72", LocalDate.of(2026, 8, 1), "VND", 3,
                new BigDecimal("900000"), new BigDecimal("300000"), "Average posted spending across the previous 3 months");
        when(budgetLimitSuggestionService.list(userId, YearMonth.of(2026, 8), "VND"))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/budgets/suggestions")
                        .param("period", "2026-08")
                        .param("currency", "VND")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Food"))
                .andExpect(jsonPath("$[0].suggestedLimit").value(300000));

        verify(budgetLimitSuggestionService).list(userId, YearMonth.of(2026, 8), "VND");
    }
}
