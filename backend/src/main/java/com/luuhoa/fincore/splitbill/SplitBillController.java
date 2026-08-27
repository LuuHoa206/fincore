package com.luuhoa.fincore.splitbill;

import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.transaction.TransactionResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/split-bills")
public class SplitBillController {

    private final SplitBillService splitBillService;

    public SplitBillController(SplitBillService splitBillService) {
        this.splitBillService = splitBillService;
    }

    @GetMapping
    List<SplitBillResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return splitBillService.list(userId(jwt));
    }

    @GetMapping("/{billId}")
    SplitBillResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID billId) {
        return splitBillService.get(userId(jwt), billId);
    }

    @PostMapping
    ResponseEntity<SplitBillResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSplitBillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(splitBillService.create(userId(jwt), request, idempotencyKey));
    }

    @PostMapping("/{billId}/participants/{participantId}/payments")
    ResponseEntity<SplitBillResponse> recordPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID billId,
            @PathVariable UUID participantId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RecordSplitBillPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(splitBillService.recordPayment(userId(jwt), billId, participantId, request, idempotencyKey));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
