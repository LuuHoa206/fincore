package com.luuhoa.fincore.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.UserResponse;
import com.luuhoa.fincore.transaction.WalletLedgerReportingService;
import com.luuhoa.fincore.transaction.WalletLedgerReportingService.WalletLedgerSnapshot;
import com.luuhoa.fincore.transaction.ReconciliationAdjustmentCommand;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.wallet.WalletResponse;
import com.luuhoa.fincore.wallet.WalletService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares a user-entered statement balance with the ledger. A preview is
 * read-only; a separately confirmed adjustment delegates all money changes to
 * the transaction module.
 */
@Service
public class WalletReconciliationService {

    private final AuthService authService;
    private final WalletService walletService;
    private final WalletLedgerReportingService walletLedgerReportingService;
    private final TransactionService transactionService;

    public WalletReconciliationService(
            AuthService authService,
            WalletService walletService,
            WalletLedgerReportingService walletLedgerReportingService,
            TransactionService transactionService) {
        this.authService = authService;
        this.walletService = walletService;
        this.walletLedgerReportingService = walletLedgerReportingService;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public WalletReconciliationPreview preview(UUID userId, WalletReconciliationRequest request) {
        UserResponse user = authService.currentUser(userId);
        ZoneId zoneId = ZoneId.of(user.timeZone());
        LocalDate today = LocalDate.now(zoneId);
        if (request.statementDate().isAfter(today)) {
            throw new IllegalArgumentException("statementDate cannot be in the future");
        }

        WalletResponse wallet = walletService.get(userId, request.walletId());
        Instant endExclusive = request.statementDate().plusDays(1).atStartOfDay(zoneId).toInstant();
        WalletLedgerSnapshot ledger = walletLedgerReportingService.balanceUntil(userId, wallet.id(), endExclusive);
        BigDecimal statementBalance = request.statementBalance();
        BigDecimal difference = statementBalance.subtract(ledger.balance());

        return new WalletReconciliationPreview(
                wallet.id(),
                wallet.name(),
                wallet.currency(),
                request.statementDate(),
                user.timeZone(),
                ledger.balance(),
                statementBalance,
                difference,
                ledger.transactionCount(),
                difference.compareTo(BigDecimal.ZERO) == 0 ? ReconciliationStatus.MATCHED : ReconciliationStatus.DIFFERENT);
    }

    public TransactionResponse confirmAdjustment(
            UUID userId,
            WalletReconciliationAdjustmentRequest request,
            String idempotencyKey) {
        UserResponse user = authService.currentUser(userId);
        ZoneId zoneId = ZoneId.of(user.timeZone());
        if (request.statementDate().isAfter(LocalDate.now(zoneId))) {
            throw new IllegalArgumentException("statementDate cannot be in the future");
        }
        // Ownership and the final ledger comparison are repeated under the
        // financial write lock by TransactionService.
        walletService.get(userId, request.walletId());
        Instant endExclusive = request.statementDate().plusDays(1).atStartOfDay(zoneId).toInstant();
        return transactionService.createReconciliationAdjustment(userId,
                new ReconciliationAdjustmentCommand(
                        request.walletId(),
                        request.statementDate(),
                        request.statementBalance(),
                        endExclusive,
                        request.reason()),
                idempotencyKey);
    }
}
