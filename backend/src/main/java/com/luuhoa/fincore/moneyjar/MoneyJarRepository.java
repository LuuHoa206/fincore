package com.luuhoa.fincore.moneyjar;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MoneyJarRepository extends JpaRepository<MoneyJar, UUID> {

    List<MoneyJar> findAllByUserIdAndArchivedFalseOrderByCreatedAtAsc(UUID userId);

    Optional<MoneyJar> findByIdAndUserIdAndArchivedFalse(UUID jarId, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select jar from MoneyJar jar
            where jar.user.id = :userId and jar.archived = false
            order by jar.id
            """)
    List<MoneyJar> findAllActiveByUserIdForUpdate(@Param("userId") UUID userId);
}
