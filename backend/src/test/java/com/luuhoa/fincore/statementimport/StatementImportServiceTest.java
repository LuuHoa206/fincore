package com.luuhoa.fincore.statementimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.UserResponse;
import com.luuhoa.fincore.transaction.TransactionCreateResult;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.transaction.TransactionStatus;
import com.luuhoa.fincore.transaction.TransactionType;
import com.luuhoa.fincore.wallet.WalletResponse;
import com.luuhoa.fincore.wallet.WalletService;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementImportServiceTest {

    @Mock private AuthService authService;
    @Mock private WalletService walletService;
    @Mock private CategoryService categoryService;
    @Mock private TransactionService transactionService;

    private StatementImportService service;

    @BeforeEach
    void setUp() {
        service = new StatementImportService(authService, walletService, categoryService, transactionService);
    }

    @Test
    void previewsLocalizedSemicolonRowsAndMarksExistingRecordsAsDuplicates() {
        UUID userId = UUID.randomUUID();
        StatementImportRequest request = request("""
                date;type;amount;description;notes
                21/08/2026 08:30;THU;12.000.000;Luong thang 8;Cong ty ABC
                22/08/2026;CHI;150.000;An trua;Nhom 3
                """);
        commonContext(userId, request.walletId());
        when(transactionService.hasRecordedIdempotencyKey(eq(userId), anyString())).thenReturn(false, true);

        StatementImportPreview preview = service.preview(userId, request);

        assertThat(preview.totalRows()).isEqualTo(2);
        assertThat(preview.readyCount()).isEqualTo(1);
        assertThat(preview.duplicateCount()).isEqualTo(1);
        assertThat(preview.invalidCount()).isZero();
        assertThat(preview.rows().getFirst().occurredAt()).isEqualTo(Instant.parse("2026-08-21T01:30:00Z"));
        assertThat(preview.rows().get(1).status()).isEqualTo(StatementImportRowStatus.DUPLICATE);
    }

    @Test
    void marksRowsInvalidWhenTheirNeededCategoryWasNotSelected() {
        UUID userId = UUID.randomUUID();
        StatementImportRequest request = new StatementImportRequest(UUID.randomUUID(), null, UUID.randomUUID(), """
                date,type,amount,description
                2026-08-21,INCOME,5000000,Salary
                """);
        commonContext(userId, request.walletId());

        StatementImportPreview preview = service.preview(userId, request);

        assertThat(preview.invalidCount()).isEqualTo(1);
        assertThat(preview.rows().getFirst().reason()).contains("income category");
    }

    @Test
    void confirmsReadyRowsThroughTheExistingTransactionWorkflow() {
        UUID userId = UUID.randomUUID();
        StatementImportRequest request = request("""
                date,type,amount,description
                2026-08-21,INCOME,5000000,Salary
                2026-08-22,EXPENSE,150000,Lunch
                """);
        commonContext(userId, request.walletId());
        when(transactionService.hasRecordedIdempotencyKey(eq(userId), anyString())).thenReturn(false);
        when(transactionService.createWithOutcome(eq(userId), any(), anyString()))
                .thenReturn(new TransactionCreateResult(transaction(TransactionType.INCOME), true),
                        new TransactionCreateResult(transaction(TransactionType.EXPENSE), true));

        StatementImportResult result = service.confirm(userId, request);

        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.skippedDuplicateCount()).isZero();
        assertThat(result.transactions()).hasSize(2);
        verify(transactionService, times(2)).createWithOutcome(eq(userId), any(), anyString());
    }

    private StatementImportRequest request(String csvText) {
        return new StatementImportRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), csvText);
    }

    private void commonContext(UUID userId, UUID walletId) {
        when(authService.currentUser(userId)).thenReturn(new UserResponse(
                userId, "owner@example.com", "Owner", "VND", "Asia/Ho_Chi_Minh", Set.of("ROLE_USER"), Instant.now()));
        when(walletService.get(userId, walletId)).thenReturn(new WalletResponse(
                walletId, "Main wallet", WalletType.BANK, "VND", new BigDecimal("20000000"), false, Instant.now(), Instant.now()));
    }

    private TransactionResponse transaction(TransactionType type) {
        return new TransactionResponse(UUID.randomUUID(), UUID.randomUUID(), "Main wallet", null, null,
                UUID.randomUUID(), type == TransactionType.INCOME ? "Salary" : "Food", null, null, type,
                TransactionStatus.POSTED, new BigDecimal("100000"), "VND", "Imported entry", null,
                Instant.now(), Instant.now(), null);
    }
}
