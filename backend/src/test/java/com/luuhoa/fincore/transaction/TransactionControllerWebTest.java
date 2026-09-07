package com.luuhoa.fincore.transaction;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.luuhoa.fincore.config.SecurityConfig;
import com.luuhoa.fincore.shared.api.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "debug=false",
        "logging.level.root=WARN",
        "logging.level.org.springframework=WARN"
})
class TransactionControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private TransactionCsvExportService transactionCsvExportService;

    @Test
    void rejectsAnUnauthenticatedFinancialWrite() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void rejectsAnUnauthenticatedTransactionExport() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/export"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"));

        verifyNoInteractions(transactionService, transactionCsvExportService);
    }

    @Test
    void exportsOnlyTheAuthenticatedUsersFilteredTransactionHistory() throws Exception {
        UUID userId = UUID.randomUUID();
        byte[] csv = "\uFEFFMã giao dịch\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(transactionCsvExportService.export(eq(userId), isNull(), isNull(), isNull(), isNull())).thenReturn(csv);

        mockMvc.perform(get("/api/v1/transactions/export")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(csv));

        verify(transactionCsvExportService).export(userId, null, null, null, null);
    }

    @Test
    void rejectsInvalidTransactionDataBeforeCallingTheService() throws Exception {
        UUID userId = UUID.randomUUID();
        String request = """
                {
                  "walletId": "%s",
                  "categoryId": "%s",
                  "transactionType": "EXPENSE",
                  "amount": 0,
                  "description": "",
                  "occurredAt": "2026-08-27T10:00:00Z"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());

        verifyNoInteractions(transactionService);
    }

    @Test
    void permitsPreflightOnlyFromConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/transactions")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void rejectsPreflightFromAnUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/transactions")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
