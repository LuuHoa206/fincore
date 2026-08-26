package com.luuhoa.fincore.allocationrule;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/allocation-rules")
public class AllocationRuleController {

    private final AllocationRuleService allocationRuleService;

    public AllocationRuleController(AllocationRuleService allocationRuleService) {
        this.allocationRuleService = allocationRuleService;
    }

    @GetMapping
    List<AllocationRuleResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return allocationRuleService.list(userId(jwt));
    }

    @PostMapping
    ResponseEntity<AllocationRuleResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAllocationRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(allocationRuleService.create(userId(jwt), request));
    }

    @PatchMapping("/{ruleId}")
    AllocationRuleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ruleId, @Valid @RequestBody UpdateAllocationRuleRequest request) {
        return allocationRuleService.update(userId(jwt), ruleId, request);
    }

    @DeleteMapping("/{ruleId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ruleId) {
        allocationRuleService.delete(userId(jwt), ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preview")
    AllocationPreviewResponse preview(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID walletId, @RequestParam BigDecimal amount) {
        return allocationRuleService.previewIncomeAllocation(userId(jwt), walletId, amount);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
