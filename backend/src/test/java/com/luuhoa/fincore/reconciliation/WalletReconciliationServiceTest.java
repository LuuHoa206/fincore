package com.luuhoa.fincore.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.UserResponse;
import com.luuhoa.fincore.transaction.WalletLedgerReportingService;
import com.luuhoa.fincore.transaction.WalletLedgerReportingService.WalletLedgerSnapshot;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.wallet.WalletResponse;
import com.luuhoa.fincore.wallet.WalletService;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletReconciliationServiceTest {

    @Mock private AuthService authService;
    @Mock private WalletService walletService;
    @Mock private WalletLedgerReportingService walletLedgerReportingService;
    @Mock private TransactionService transactionService;

    private WalletReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new WalletReconciliationService(authService, walletService, walletLedgerReportingService, transactionService);
    }

    @Test
    void comparesTheStatementWithLedgerEntriesThroughTheEndOfTheSelectedLocalDay() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        LocalDate statementDate = LocalDate.of(2026, 8, 26);
        when(authService.currentUser(userId)).thenReturn(user(userId));
        when(walletService.get(userId, walletId)).thenReturn(wallet(walletId));
        when(walletLedgerReportingService.balanceUntil(userId, walletId, Instant.parse("2026-08-26T17:00:00Z")))
                .thenReturn(new WalletLedgerSnapshot(new BigDecimal("1250000.0000"), 8));

        WalletReconciliationPreview preview = service.preview(userId,
                new WalletReconciliationRequest(walletId, statementDate, new BigDecimal("1250000.0000")));

        assertThat(preview.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(preview.difference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.transactionCount()).isEqualTo(8);
        verify(walletLedgerReportingService).balanceUntil(userId, walletId, Instant.parse("2026-08-26T17:00:00Z"));
    }

    @Test
    void reportsStatementMinusLedgerAsTheDifferenceWithoutChangingTheWallet() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        when(authService.currentUser(userId)).thenReturn(user(userId));
        when(walletService.get(userId, walletId)).thenReturn(wallet(walletId));
        when(walletLedgerReportingService.balanceUntil(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq(walletId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new WalletLedgerSnapshot(new BigDecimal("900000"), 3));

        WalletReconciliationPreview preview = service.preview(userId,
                new WalletReconciliationRequest(walletId, LocalDate.of(2026, 8, 20), new BigDecimal("1000000")));

        assertThat(preview.status()).isEqualTo(ReconciliationStatus.DIFFERENT);
        assertThat(preview.difference()).isEqualByComparingTo("100000");
        assertThat(preview.ledgerBalance()).isEqualByComparingTo("900000");
        assertThat(preview.statementBalance()).isEqualByComparingTo("1000000");
    }

    @Test
    void delegatesAConfirmedAdjustmentWithTheUserLocalStatementCutoff() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        when(authService.currentUser(userId)).thenReturn(user(userId));
        when(walletService.get(userId, walletId)).thenReturn(wallet(walletId));

        service.confirmAdjustment(userId,
                new WalletReconciliationAdjustmentRequest(walletId, LocalDate.of(2026, 8, 26),
                        new BigDecimal("1250000"), "Khớp số dư cuối ngày trên sao kê"),
                "reconciliation-26-08-2026");

        org.mockito.ArgumentCaptor<com.luuhoa.fincore.transaction.ReconciliationAdjustmentCommand> command =
                org.mockito.ArgumentCaptor.forClass(com.luuhoa.fincore.transaction.ReconciliationAdjustmentCommand.class);
        verify(transactionService).createReconciliationAdjustment(
                org.mockito.ArgumentMatchers.eq(userId), command.capture(),
                org.mockito.ArgumentMatchers.eq("reconciliation-26-08-2026"));
        assertThat(command.getValue().endExclusive()).isEqualTo(Instant.parse("2026-08-26T17:00:00Z"));
        assertThat(command.getValue().reason()).isEqualTo("Khớp số dư cuối ngày trên sao kê");
    }

    private UserResponse user(UUID userId) {
        return new UserResponse(userId, "owner@example.com", "Owner", "VND", "Asia/Ho_Chi_Minh", Set.of("ROLE_USER"), Instant.now());
    }

    private WalletResponse wallet(UUID walletId) {
        return new WalletResponse(walletId, "Tai khoan chinh", WalletType.BANK, "VND", BigDecimal.ZERO, false, Instant.now(), Instant.now());
    }
}
