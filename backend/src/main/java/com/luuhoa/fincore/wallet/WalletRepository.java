package com.luuhoa.fincore.wallet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findAllByUserIdAndArchivedFalseOrderByCreatedAtAsc(UUID userId);

    Optional<Wallet> findByIdAndUserIdAndArchivedFalse(UUID id, UUID userId);
}
