package com.luuhoa.fincore.moneyjar;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jars")
public class MoneyJarController {

    private final MoneyJarService moneyJarService;

    public MoneyJarController(MoneyJarService moneyJarService) {
        this.moneyJarService = moneyJarService;
    }

    @GetMapping
    List<MoneyJarResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return moneyJarService.list(userId(jwt));
    }

    @PostMapping
    ResponseEntity<MoneyJarResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMoneyJarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moneyJarService.create(userId(jwt), request));
    }

    @PatchMapping("/{jarId}")
    MoneyJarResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID jarId,
            @Valid @RequestBody UpdateMoneyJarRequest request) {
        return moneyJarService.update(userId(jwt), jarId, request);
    }

    @DeleteMapping("/{jarId}")
    ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID jarId) {
        moneyJarService.archive(userId(jwt), jarId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{jarId}/allocate")
    JarAllocationResponse allocate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID jarId,
            @Valid @RequestBody ChangeJarAllocationRequest request) {
        return moneyJarService.allocate(userId(jwt), jarId, request);
    }

    @PostMapping("/{jarId}/release")
    JarAllocationResponse release(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID jarId,
            @Valid @RequestBody ChangeJarAllocationRequest request) {
        return moneyJarService.release(userId(jwt), jarId, request);
    }

    @PostMapping("/transfers")
    JarTransferResponse transfer(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferBetweenJarsRequest request) {
        return moneyJarService.transfer(userId(jwt), request);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
