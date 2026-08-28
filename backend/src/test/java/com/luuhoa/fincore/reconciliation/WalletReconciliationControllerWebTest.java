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
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.config.SecurityConfig;
import com.luuhoa.fincore.shared.api.GlobalExceptionHandler;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionStatus;
import com.luuhoa.fincore.transaction.TransactionType;

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

    @Test
    void confirmsAnAdjustmentWithAnIdempotencyKey() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(walletReconciliationService.confirmAdjustment(eq(userId), any(), eq("adjustment-key")))
                .thenReturn(new TransactionResponse(transactionId, walletId, "Main", null, null, null, null,
                        null, null, TransactionType.ADJUSTMENT, TransactionStatus.POSTED,
                        new BigDecimal("100000"), "VND", "Điều chỉnh theo đối soát sao kê", "Xác nhận sao kê",
                        Instant.parse("2026-08-26T16:59:59.999999999Z"), Instant.now(), null));

        mockMvc.perform(post("/api/v1/wallet-reconciliations/adjustments")
                        .header("Idempotency-Key", "adjustment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"%s","statementDate":"2026-08-26","statementBalance":1000000,"reason":"Xác nhận sao kê"}
                                """.formatted(walletId))
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionType").value("ADJUSTMENT"));

        verify(walletReconciliationService).confirmAdjustment(
                eq(userId), any(WalletReconciliationAdjustmentRequest.class), eq("adjustment-key"));
    }
}
