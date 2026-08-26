package com.luuhoa.fincore.allocationrule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AllocationRuleRepository extends JpaRepository<AllocationRule, UUID> {

    @Query("select distinct rule from AllocationRule rule left join fetch rule.items item left join fetch item.jar where rule.user.id = :userId order by rule.createdAt asc")
    List<AllocationRule> findAllByUserIdWithItems(@Param("userId") UUID userId);

    @Query("select distinct rule from AllocationRule rule left join fetch rule.items item left join fetch item.jar where rule.id = :ruleId and rule.user.id = :userId")
    Optional<AllocationRule> findOwnedWithItems(@Param("ruleId") UUID ruleId, @Param("userId") UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    @Query("select distinct rule from AllocationRule rule left join fetch rule.items item left join fetch item.jar where rule.user.id = :userId and rule.currency = :currency and rule.enabled = true")
    Optional<AllocationRule> findEnabledByUserIdAndCurrencyAndEnabledTrue(@Param("userId") UUID userId, @Param("currency") String currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from AllocationRule rule where rule.user.id = :userId and rule.currency = :currency and rule.enabled = true")
    Optional<AllocationRule> findEnabledForUpdate(@Param("userId") UUID userId, @Param("currency") String currency);
}
