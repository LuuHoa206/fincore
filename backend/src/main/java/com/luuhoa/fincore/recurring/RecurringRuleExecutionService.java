package com.luuhoa.fincore.recurring;

import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.CreateTransactionRequest;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringRuleExecutionService {

    private final RecurringRuleRepository recurringRuleRepository;
    private final TransactionService transactionService;

    public RecurringRuleExecutionService(
            RecurringRuleRepository recurringRuleRepository,
            TransactionService transactionService) {
        this.recurringRuleRepository = recurringRuleRepository;
        this.transactionService = transactionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoRecordDueRule(UUID ruleId, Instant now) {
        RecurringRule rule = recurringRuleRepository.findByIdForUpdate(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("RECURRING_RULE_NOT_FOUND", "Recurring rule was not found"));
        if (!rule.isAutoRecord() || !rule.isDueAt(now)) {
            return false;
        }
        recordAndAdvance(rule, now);
        return true;
    }

    @Transactional
    public TransactionResponse recordDueRule(UUID userId, UUID ruleId, Instant now) {
        RecurringRule rule = recurringRuleRepository.findOwnedForUpdate(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RECURRING_RULE_NOT_FOUND", "Recurring rule was not found"));
        if (!rule.isDueAt(now)) {
            throw new ConflictException("RECURRING_RULE_NOT_DUE", "This recurring rule is not due yet");
        }
        return recordAndAdvance(rule, now);
    }

    private TransactionResponse recordAndAdvance(RecurringRule rule, Instant now) {
        Instant scheduledAt = rule.getNextRunAt();
        TransactionResponse response = transactionService.create(
                rule.getUser().getId(),
                new CreateTransactionRequest(
                        rule.getWallet().getId(),
                        rule.getCategory().getId(),
                        rule.getTransactionType(),
                        rule.getAmount(),
                        rule.getDescription(),
                        rule.getNotes(),
                        scheduledAt,
                        rule.isApplyAllocationRule()),
                idempotencyKey(rule, scheduledAt));
        rule.advanceNextRunAt(now);
        return response;
    }

    private String idempotencyKey(RecurringRule rule, Instant scheduledAt) {
        return "recurring:" + rule.getId() + ":" + scheduledAt.toEpochMilli();
    }
}
