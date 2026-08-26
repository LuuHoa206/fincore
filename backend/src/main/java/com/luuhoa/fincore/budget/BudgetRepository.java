package com.luuhoa.fincore.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findAllByUserIdAndPeriodStartAndArchivedFalseOrderByCreatedAtAsc(UUID userId, LocalDate periodStart);

    Optional<Budget> findByIdAndUserIdAndArchivedFalse(UUID budgetId, UUID userId);

    boolean existsByUserIdAndCategoryIdAndPeriodStartAndArchivedFalse(UUID userId, UUID categoryId, LocalDate periodStart);
}
