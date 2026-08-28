package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public read boundary for consumers that need a wallet's historic ledger
 * position without reaching into transaction repositories.
 */
@Service
public class WalletLedgerReportingService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public WalletLedgerReportingService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public WalletLedgerSnapshot balanceUntil(UUID userId, UUID walletId, Instant endExclusive) {
        LedgerEntryRepository.WalletLedgerBalance result = ledgerEntryRepository
                .summarizeWalletBalanceUntil(userId, walletId, endExclusive);
        return new WalletLedgerSnapshot(
                result == null || result.getBalance() == null ? BigDecimal.ZERO : result.getBalance(),
                result == null ? 0 : result.getTransactionCount());
    }

    public record WalletLedgerSnapshot(BigDecimal balance, long transactionCount) {
    }
}
