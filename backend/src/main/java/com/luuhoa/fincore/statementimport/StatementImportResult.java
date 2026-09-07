package com.luuhoa.fincore.statementimport;

import java.util.List;

import com.luuhoa.fincore.transaction.TransactionResponse;

public record StatementImportResult(
        int importedCount,
        int skippedDuplicateCount,
        List<TransactionResponse> transactions) {
}
