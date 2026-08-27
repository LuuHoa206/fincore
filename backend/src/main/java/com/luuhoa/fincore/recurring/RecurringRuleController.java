package com.luuhoa.fincore.recurring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.transaction.TransactionResponse;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recurring-rules")
public class RecurringRuleController {

    private final RecurringRuleService recurringRuleService;

    public RecurringRuleController(RecurringRuleService recurringRuleService) {
        this.recurringRuleService = recurringRuleService;
    }

    @GetMapping
    List<RecurringRuleResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return recurringRuleService.list(userId(jwt));
    }

    @PostMapping
    ResponseEntity<RecurringRuleResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRecurringRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringRuleService.create(userId(jwt), request));
    }

    @PatchMapping("/{ruleId}")
    RecurringRuleResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateRecurringRuleRequest request) {
        return recurringRuleService.update(userId(jwt), ruleId, request);
    }

    @PostMapping("/{ruleId}/record")
    ResponseEntity<TransactionResponse> recordDue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ruleId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringRuleService.recordDue(userId(jwt), ruleId, Instant.now()));
    }

    @DeleteMapping("/{ruleId}")
    ResponseEntity<Void> disable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ruleId) {
        recurringRuleService.disable(userId(jwt), ruleId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
