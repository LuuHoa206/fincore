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
import com.luuhoa.fincore.wallet.WalletResponse;
import com.luuhoa.fincore.wallet.WalletService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only comparison between a user-entered statement balance and the ledger.
 * It deliberately does not create an adjustment or modify a wallet.
 */
@Service
public class WalletReconciliationService {

    private final AuthService authService;
    private final WalletService walletService;
    private final WalletLedgerReportingService walletLedgerReportingService;

    public WalletReconciliationService(
            AuthService authService,
            WalletService walletService,
            WalletLedgerReportingService walletLedgerReportingService) {
        this.authService = authService;
        this.walletService = walletService;
        this.walletLedgerReportingService = walletLedgerReportingService;
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
}
