package com.luuhoa.fincore.transaction;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findAllByTransactionIdAndAccountKind(UUID transactionId, AccountKind accountKind);

    @Query("""
            select entry from LedgerEntry entry
            join fetch entry.transaction transaction
            join fetch entry.wallet wallet
            where transaction.id in :transactionIds
              and entry.accountKind = :accountKind
            """)
    List<LedgerEntry> findAllByTransactionIdInAndAccountKindWithWallet(
            @Param("transactionIds") List<UUID> transactionIds,
            @Param("accountKind") AccountKind accountKind);

    @Query("""
            select coalesce(sum(entry.signedAmount), 0) as balance,
                   count(distinct transaction.id) as transactionCount
            from LedgerEntry entry
            join entry.transaction transaction
            where entry.wallet.id = :walletId
              and transaction.user.id = :userId
              and transaction.occurredAt < :endExclusive
            """)
    WalletLedgerBalance summarizeWalletBalanceUntil(
            @Param("userId") UUID userId,
            @Param("walletId") UUID walletId,
            @Param("endExclusive") Instant endExclusive);

    interface WalletLedgerBalance {
        BigDecimal getBalance();

        long getTransactionCount();
    }

}
