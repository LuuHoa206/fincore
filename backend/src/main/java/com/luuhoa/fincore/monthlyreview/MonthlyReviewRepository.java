package com.luuhoa.fincore.monthlyreview;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReviewRepository extends JpaRepository<MonthlyReview, UUID> {

    Optional<MonthlyReview> findByUserIdAndPeriodStart(UUID userId, LocalDate periodStart);
}
