package com.luuhoa.fincore.transaction;

/**
 * Outcome of an idempotent transaction write. A replay returns the original
 * response with {@code created=false}, so callers never guess from balances.
 */
public record TransactionCreateResult(TransactionResponse transaction, boolean created) {
}
