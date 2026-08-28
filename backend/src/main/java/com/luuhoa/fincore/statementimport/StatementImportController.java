package com.luuhoa.fincore.statementimport;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statement-imports")
public class StatementImportController {

    private final StatementImportService statementImportService;

    public StatementImportController(StatementImportService statementImportService) {
        this.statementImportService = statementImportService;
    }

    @PostMapping("/preview")
    StatementImportPreview preview(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StatementImportRequest request) {
        return statementImportService.preview(userId(jwt), request);
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    StatementImportResult confirm(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StatementImportRequest request) {
        return statementImportService.confirm(userId(jwt), request);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
