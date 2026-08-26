package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.wallet.Wallet;

public record TransactionResponse(
        UUID id,
        UUID walletId,
        String walletName,
        TransactionType transactionType,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        String description,
        String notes,
        Instant occurredAt,
        Instant postedAt,
        UUID reversedTransactionId) {

    static TransactionResponse from(FinancialTransaction transaction, Wallet wallet) {
        FinancialTransaction reversedTransaction = transaction.getReversedTransaction();
        return new TransactionResponse(
                transaction.getId(),
                wallet.getId(),
                wallet.getName(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDescription(),
                transaction.getNotes(),
                transaction.getOccurredAt(),
                transaction.getPostedAt(),
                reversedTransaction == null ? null : reversedTransaction.getId());
    }
}
