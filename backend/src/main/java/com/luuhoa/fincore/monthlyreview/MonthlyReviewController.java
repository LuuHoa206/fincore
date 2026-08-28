package com.luuhoa.fincore.monthlyreview;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monthly-reviews")
public class MonthlyReviewController {

    private final MonthlyReviewService monthlyReviewService;

    public MonthlyReviewController(MonthlyReviewService monthlyReviewService) {
        this.monthlyReviewService = monthlyReviewService;
    }

    @GetMapping
    MonthlyReviewResponse get(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String period) {
        return monthlyReviewService.get(userId(jwt), period);
    }

    @PutMapping
    MonthlyReviewResponse save(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String period,
            @Valid @RequestBody SaveMonthlyReviewRequest request) {
        return monthlyReviewService.save(userId(jwt), period, request);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
