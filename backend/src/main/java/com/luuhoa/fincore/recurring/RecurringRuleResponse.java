package com.luuhoa.fincore.recurring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.transaction.TransactionType;

public record RecurringRuleResponse(
        UUID id,
        String name,
        UUID walletId,
        String walletName,
        UUID categoryId,
        String categoryName,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        String description,
        String notes,
        RecurringFrequency frequency,
        int scheduleDay,
        Instant nextRunAt,
        boolean autoRecord,
        boolean enabled,
        boolean applyAllocationRule,
        Instant createdAt,
        Instant updatedAt) {

    public static RecurringRuleResponse from(RecurringRule rule) {
        return new RecurringRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getWallet().getId(),
                rule.getWallet().getName(),
                rule.getCategory().getId(),
                rule.getCategory().getName(),
                rule.getTransactionType(),
                rule.getAmount(),
                rule.getCurrency(),
                rule.getDescription(),
                rule.getNotes(),
                rule.getFrequency(),
                rule.getScheduleDay(),
                rule.getNextRunAt(),
                rule.isAutoRecord(),
                rule.isEnabled(),
                rule.isApplyAllocationRule(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
