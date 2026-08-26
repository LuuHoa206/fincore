package com.luuhoa.fincore.wallet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findAllByUserIdAndArchivedFalseOrderByCreatedAtAsc(UUID userId);

    Optional<Wallet> findByIdAndUserIdAndArchivedFalse(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select wallet from Wallet wallet
            where wallet.id = :walletId and wallet.user.id = :userId and wallet.archived = false
            """)
    Optional<Wallet> findOwnedForUpdate(@Param("walletId") UUID walletId, @Param("userId") UUID userId);
}
