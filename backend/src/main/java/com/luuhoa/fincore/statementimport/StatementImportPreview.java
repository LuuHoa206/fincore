package com.luuhoa.fincore.statementimport;

import java.util.List;
import java.util.UUID;

public record StatementImportPreview(
        UUID walletId,
        String currency,
        int totalRows,
        int readyCount,
        int duplicateCount,
        int invalidCount,
        List<StatementImportRow> rows) {
}
