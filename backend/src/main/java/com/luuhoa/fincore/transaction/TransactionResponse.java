package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.wallet.Wallet;

public record TransactionResponse(
        UUID id,
        UUID walletId,
        String walletName,
        UUID counterpartyWalletId,
        String counterpartyWalletName,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        TransactionType transactionType,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        String description,
        String notes,
        Instant occurredAt,
        Instant postedAt,
        UUID reversedTransactionId,
        BigDecimal walletChange) {

    public TransactionResponse(
            UUID id,
            UUID walletId,
            String walletName,
            UUID counterpartyWalletId,
            String counterpartyWalletName,
            UUID categoryId,
            String categoryName,
            String categoryIcon,
            String categoryColor,
            TransactionType transactionType,
            TransactionStatus status,
            BigDecimal amount,
            String currency,
            String description,
            String notes,
            Instant occurredAt,
            Instant postedAt,
            UUID reversedTransactionId) {
        this(id, walletId, walletName, counterpartyWalletId, counterpartyWalletName,
                categoryId, categoryName, categoryIcon, categoryColor, transactionType,
                status, amount, currency, description, notes, occurredAt, postedAt,
                reversedTransactionId, null);
    }

    static TransactionResponse from(FinancialTransaction transaction, Wallet wallet) {
        return from(transaction, wallet, null, null);
    }

    static TransactionResponse from(FinancialTransaction transaction, Wallet wallet, Wallet counterpartyWallet) {
        return from(transaction, wallet, counterpartyWallet, null);
    }

    static TransactionResponse from(FinancialTransaction transaction, Wallet wallet, BigDecimal walletChange) {
        return from(transaction, wallet, null, walletChange);
    }

    static TransactionResponse from(
            FinancialTransaction transaction,
            Wallet wallet,
            Wallet counterpartyWallet,
            BigDecimal walletChange) {
        FinancialTransaction reversedTransaction = transaction.getReversedTransaction();
        var category = transaction.getCategory();
        return new TransactionResponse(
                transaction.getId(),
                wallet.getId(),
                wallet.getName(),
                counterpartyWallet == null ? null : counterpartyWallet.getId(),
                counterpartyWallet == null ? null : counterpartyWallet.getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getIcon(),
                category == null ? null : category.getColor(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDescription(),
                transaction.getNotes(),
                transaction.getOccurredAt(),
                transaction.getPostedAt(),
                reversedTransaction == null ? null : reversedTransaction.getId(),
                walletChange);
    }
}
