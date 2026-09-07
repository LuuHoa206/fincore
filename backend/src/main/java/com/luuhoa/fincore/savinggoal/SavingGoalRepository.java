package com.luuhoa.fincore.savinggoal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, UUID> {

    List<SavingGoal> findAllByUserIdAndStatusNotOrderByCreatedAtAsc(UUID userId, SavingGoalStatus status);

    Optional<SavingGoal> findByIdAndUserIdAndStatusNot(UUID goalId, UUID userId, SavingGoalStatus status);

    boolean existsByUserIdAndJarIdAndStatusIn(UUID userId, UUID jarId, List<SavingGoalStatus> statuses);
}
