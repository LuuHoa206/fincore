package com.luuhoa.fincore.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletLedgerReportingServiceTest {

    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private LedgerEntryRepository.WalletLedgerBalance balance;

    @Test
    void exposesAnEmptyBalanceSafelyWhenTheWalletHasNoLedgerEntries() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Instant endExclusive = Instant.parse("2026-08-26T17:00:00Z");
        when(ledgerEntryRepository.summarizeWalletBalanceUntil(userId, walletId, endExclusive)).thenReturn(null);

        WalletLedgerReportingService.WalletLedgerSnapshot result = new WalletLedgerReportingService(ledgerEntryRepository)
                .balanceUntil(userId, walletId, endExclusive);

        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.transactionCount()).isZero();
    }

    @Test
    void preservesTheRepositoryTotalAndDistinctTransactionCount() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Instant endExclusive = Instant.parse("2026-08-26T17:00:00Z");
        when(balance.getBalance()).thenReturn(new BigDecimal("123.4500"));
        when(balance.getTransactionCount()).thenReturn(4L);
        when(ledgerEntryRepository.summarizeWalletBalanceUntil(userId, walletId, endExclusive)).thenReturn(balance);

        WalletLedgerReportingService.WalletLedgerSnapshot result = new WalletLedgerReportingService(ledgerEntryRepository)
                .balanceUntil(userId, walletId, endExclusive);

        assertThat(result.balance()).isEqualByComparingTo("123.4500");
        assertThat(result.transactionCount()).isEqualTo(4);
    }
}
