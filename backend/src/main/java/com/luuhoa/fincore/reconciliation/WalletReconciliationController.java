package com.luuhoa.fincore.reconciliation;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet-reconciliations")
public class WalletReconciliationController {

    private final WalletReconciliationService walletReconciliationService;

    public WalletReconciliationController(WalletReconciliationService walletReconciliationService) {
        this.walletReconciliationService = walletReconciliationService;
    }

    @PostMapping("/preview")
    WalletReconciliationPreview preview(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WalletReconciliationRequest request) {
        return walletReconciliationService.preview(UUID.fromString(jwt.getSubject()), request);
    }
}
