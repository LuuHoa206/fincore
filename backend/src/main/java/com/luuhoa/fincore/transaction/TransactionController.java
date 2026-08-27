package com.luuhoa.fincore.transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionCsvExportService transactionCsvExportService;

    public TransactionController(
            TransactionService transactionService,
            TransactionCsvExportService transactionCsvExportService) {
        this.transactionService = transactionService;
        this.transactionCsvExportService = transactionCsvExportService;
    }

    @GetMapping
    TransactionPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestParam(required = false) TransactionType transactionType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String query,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Instant from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Instant to,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return transactionService.search(userId(jwt), transactionType, query, from, to, page, size);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    ResponseEntity<byte[]> export(
            @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestParam(required = false) TransactionType transactionType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String query,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Instant from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Instant to) {
        byte[] csv = transactionCsvExportService.export(userId(jwt), transactionType, query, from, to);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("fincore-transactions-" + LocalDate.now() + ".csv")
                .build());
        headers.setContentLength(csv.length);
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {
        return transactionService.create(userId(jwt), request, idempotencyKey);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse transfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateWalletTransferRequest request) {
        return transactionService.createTransfer(userId(jwt), request, idempotencyKey);
    }

    @PostMapping("/{transactionId}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse reverse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId) {
        return transactionService.reverse(userId(jwt), transactionId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
