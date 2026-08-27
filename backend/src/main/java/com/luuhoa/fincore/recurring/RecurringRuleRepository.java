package com.luuhoa.fincore.recurring;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, UUID> {

    @Query("""
            select rule from RecurringRule rule
            join fetch rule.wallet
            join fetch rule.category
            where rule.user.id = :userId
            order by rule.nextRunAt asc
            """)
    List<RecurringRule> findAllByUserIdWithDetails(@Param("userId") UUID userId);

    @Query("""
            select rule.id from RecurringRule rule
            where rule.enabled = true
              and rule.autoRecord = true
              and rule.nextRunAt <= :now
            order by rule.nextRunAt asc
            """)
    List<UUID> findDueAutoRecordIds(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select rule from RecurringRule rule
            join fetch rule.user
            join fetch rule.wallet
            join fetch rule.category
            where rule.id = :ruleId
            """)
    Optional<RecurringRule> findByIdForUpdate(@Param("ruleId") UUID ruleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select rule from RecurringRule rule
            join fetch rule.user
            join fetch rule.wallet
            join fetch rule.category
            where rule.id = :ruleId
              and rule.user.id = :userId
            """)
    Optional<RecurringRule> findOwnedForUpdate(@Param("ruleId") UUID ruleId, @Param("userId") UUID userId);

    Optional<RecurringRule> findByIdAndUserId(UUID ruleId, UUID userId);
}
