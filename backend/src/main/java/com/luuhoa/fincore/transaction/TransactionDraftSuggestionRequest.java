package com.luuhoa.fincore.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for the read-only quick-entry assistant. */
public record TransactionDraftSuggestionRequest(
        @NotBlank @Size(max = 255) String text,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        TransactionType currentTransactionType) {
}
