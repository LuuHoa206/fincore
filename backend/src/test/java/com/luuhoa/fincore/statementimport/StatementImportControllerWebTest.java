package com.luuhoa.fincore.statementimport;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@WebMvcTest(StatementImportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class StatementImportControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatementImportService statementImportService;

    @Test
    void previewsTheAuthenticatedUsersStatementRows() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        StatementImportRequest request = new StatementImportRequest(walletId, UUID.randomUUID(), UUID.randomUUID(),
                "date,type,amount,description\n2026-08-21,INCOME,5000000,Salary");
        when(statementImportService.preview(userId, request))
                .thenReturn(new StatementImportPreview(walletId, "VND", 1, 1, 0, 0, List.of()));

        mockMvc.perform(post("/api/v1/statement-imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"%s","incomeCategoryId":"%s","expenseCategoryId":"%s","csvText":"date,type,amount,description\\n2026-08-21,INCOME,5000000,Salary"}
                                """.formatted(walletId, request.incomeCategoryId(), request.expenseCategoryId()))
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyCount").value(1))
                .andExpect(jsonPath("$.currency").value("VND"));

        verify(statementImportService).preview(eq(userId), eq(request));
    }
}
