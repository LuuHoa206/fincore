package com.luuhoa.fincore.transaction;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transaction-drafts")
public class TransactionDraftAssistantController {

    private final TransactionDraftAssistantService transactionDraftAssistantService;

    public TransactionDraftAssistantController(TransactionDraftAssistantService transactionDraftAssistantService) {
        this.transactionDraftAssistantService = transactionDraftAssistantService;
    }

    @PostMapping("/suggestion")
    TransactionDraftSuggestionResponse suggest(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransactionDraftSuggestionRequest request) {
        return transactionDraftAssistantService.suggest(UUID.fromString(jwt.getSubject()), request);
    }
}
