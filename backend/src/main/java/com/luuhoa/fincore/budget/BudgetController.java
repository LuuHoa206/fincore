package com.luuhoa.fincore.budget;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetLimitSuggestionService budgetLimitSuggestionService;

    public BudgetController(BudgetService budgetService, BudgetLimitSuggestionService budgetLimitSuggestionService) {
        this.budgetService = budgetService;
        this.budgetLimitSuggestionService = budgetLimitSuggestionService;
    }

    @GetMapping
    List<BudgetResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth period) {
        return budgetService.list(userId(jwt), period);
    }

    @GetMapping("/suggestions")
    List<BudgetLimitSuggestionResponse> suggestions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
            @RequestParam(required = false) String currency) {
        return budgetLimitSuggestionService.list(userId(jwt), period, currency);
    }

    @PostMapping
    ResponseEntity<BudgetResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.create(userId(jwt), request));
    }

    @PatchMapping("/{budgetId}")
    BudgetResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID budgetId,
            @Valid @RequestBody UpdateBudgetRequest request) {
        return budgetService.update(userId(jwt), budgetId, request);
    }

    @DeleteMapping("/{budgetId}")
    ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID budgetId) {
        budgetService.archive(userId(jwt), budgetId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
