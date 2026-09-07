package com.luuhoa.fincore.savinggoal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/saving-goals")
public class SavingGoalController {

    private final SavingGoalService savingGoalService;

    public SavingGoalController(SavingGoalService savingGoalService) {
        this.savingGoalService = savingGoalService;
    }

    @GetMapping
    List<SavingGoalResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return savingGoalService.list(userId(jwt));
    }

    @PostMapping
    ResponseEntity<SavingGoalResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSavingGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savingGoalService.create(userId(jwt), request));
    }

    @PatchMapping("/{goalId}")
    SavingGoalResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID goalId, @Valid @RequestBody UpdateSavingGoalRequest request) {
        return savingGoalService.update(userId(jwt), goalId, request);
    }

    @PostMapping("/{goalId}/status")
    SavingGoalResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID goalId, @Valid @RequestBody ChangeSavingGoalStatusRequest request) {
        return savingGoalService.changeStatus(userId(jwt), goalId, request);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
