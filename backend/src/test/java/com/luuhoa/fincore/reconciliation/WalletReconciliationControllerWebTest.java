package com.luuhoa.fincore.reconciliation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(WalletReconciliationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WalletReconciliationControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WalletReconciliationService walletReconciliationService;

    @Test
    void previewsForTheAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        when(walletReconciliationService.preview(eq(userId), any()))
                .thenReturn(new WalletReconciliationPreview(walletId, "Main", "VND", LocalDate.of(2026, 8, 26),
                        "Asia/Ho_Chi_Minh", new BigDecimal("900000"), new BigDecimal("1000000"),
                        new BigDecimal("100000"), 4, ReconciliationStatus.DIFFERENT));

        mockMvc.perform(post("/api/v1/wallet-reconciliations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"%s","statementDate":"2026-08-26","statementBalance":1000000}
                                """.formatted(walletId))
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.status").value("DIFFERENT"))
                .andExpect(jsonPath("$.difference").value(100000));

        verify(walletReconciliationService).preview(eq(userId), any(WalletReconciliationRequest.class));
    }
}
