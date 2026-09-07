package com.luuhoa.fincore.statementimport;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * CSV uses the required header: date,type,amount,description,notes.
 * Semicolon-delimited files are supported too so a decimal comma stays safe.
 */
public record StatementImportRequest(
        @NotNull UUID walletId,
        UUID incomeCategoryId,
        UUID expenseCategoryId,
        @NotBlank @Size(max = 200_000) String csvText) {
}
