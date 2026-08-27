package com.luuhoa.fincore.transaction;

import java.util.UUID;

/**
 * Module extension point for transactions whose domain state would become inconsistent after a direct reversal.
 */
public interface TransactionReversalGuard {

    void assertCanReverse(UUID userId, UUID transactionId);
}
