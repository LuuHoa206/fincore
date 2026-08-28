package com.luuhoa.fincore.transaction;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(TransactionDraftAssistantController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TransactionDraftAssistantControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TransactionDraftAssistantService transactionDraftAssistantService;

    @Test
    void rejectsUnauthenticatedDraftRequests() throws Exception {
        mockMvc.perform(post("/api/v1/transaction-drafts/suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Coffee 45k\",\"currency\":\"VND\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transactionDraftAssistantService);
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersDraftSuggestion() throws Exception {
        UUID userId = UUID.randomUUID();
        TransactionDraftSuggestionRequest request = new TransactionDraftSuggestionRequest("Coffee 45k", "VND", TransactionType.EXPENSE);
        when(transactionDraftAssistantService.suggest(eq(userId), eq(request))).thenReturn(new TransactionDraftSuggestionResponse(
                "Coffee 45k", BigDecimal.valueOf(45_000), TransactionType.EXPENSE, LocalDate.of(2026, 8, 28), List.of("Amount found"), List.of()));

        mockMvc.perform(post("/api/v1/transaction-drafts/suggestion")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Coffee 45k\",\"currency\":\"VND\",\"currentTransactionType\":\"EXPENSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedAmount").value(45000))
                .andExpect(jsonPath("$.suggestedTransactionType").value("EXPENSE"));

        verify(transactionDraftAssistantService).suggest(userId, request);
    }
}
