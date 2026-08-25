package com.luuhoa.fincore.wallet;

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
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    List<WalletResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return walletService.list(userId(jwt));
    }

    @GetMapping("/{walletId}")
    WalletResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        return walletService.get(userId(jwt), walletId);
    }

    @PostMapping
    ResponseEntity<WalletResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWalletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(walletService.create(userId(jwt), request));
    }

    @PatchMapping("/{walletId}")
    WalletResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID walletId,
            @Valid @RequestBody UpdateWalletRequest request) {
        return walletService.update(userId(jwt), walletId, request);
    }

    @DeleteMapping("/{walletId}")
    ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        walletService.archive(userId(jwt), walletId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
