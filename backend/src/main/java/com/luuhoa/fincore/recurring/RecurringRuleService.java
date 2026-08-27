package com.luuhoa.fincore.recurring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionType;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletService;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringRuleService {

    private final RecurringRuleRepository recurringRuleRepository;
    private final UserAccountRepository userRepository;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final RecurringRuleExecutionService executionService;

    public RecurringRuleService(
            RecurringRuleRepository recurringRuleRepository,
            UserAccountRepository userRepository,
            WalletService walletService,
            CategoryService categoryService,
            RecurringRuleExecutionService executionService) {
        this.recurringRuleRepository = recurringRuleRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.categoryService = categoryService;
        this.executionService = executionService;
    }

    @Transactional(readOnly = true)
    public List<RecurringRuleResponse> list(UUID userId) {
        return recurringRuleRepository.findAllByUserIdWithDetails(userId).stream()
                .map(RecurringRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecurringRuleResponse> upcoming(UUID userId, int limit) {
        if (limit < 1 || limit > 10) {
            throw new IllegalArgumentException("Upcoming recurring-rule limit must be between 1 and 10");
        }
        return recurringRuleRepository.findEnabledByUserIdWithDetails(userId, PageRequest.of(0, limit)).stream()
                .map(RecurringRuleResponse::from)
                .toList();
    }

    @Transactional
    public RecurringRuleResponse create(UUID userId, CreateRecurringRuleRequest request) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        RecurringRule rule = createRule(user, userId, request);
        return RecurringRuleResponse.from(recurringRuleRepository.save(rule));
    }

    @Transactional
    public RecurringRuleResponse update(UUID userId, UUID ruleId, UpdateRecurringRuleRequest request) {
        RecurringRule rule = recurringRuleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RECURRING_RULE_NOT_FOUND", "Recurring rule was not found"));
        Wallet wallet = walletService.requireOwnedActiveWallet(userId, request.walletId());
        Category category = categoryService.requireAvailableForTransaction(userId, request.categoryId(), categoryTypeFor(request.transactionType()));
        validateRequest(request.transactionType(), request.amount(), wallet.getCurrency(), request.applyAllocationRule());
        rule.update(
                wallet,
                category,
                request.name().trim(),
                request.transactionType(),
                normalizeAmount(request.amount(), wallet.getCurrency()),
                wallet.getCurrency(),
                request.description().trim(),
                normalizeNotes(request.notes()),
                request.frequency(),
                request.nextRunAt(),
                request.autoRecord(),
                request.enabled(),
                request.applyAllocationRule());
        return RecurringRuleResponse.from(rule);
    }

    @Transactional
    public void disable(UUID userId, UUID ruleId) {
        RecurringRule rule = recurringRuleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RECURRING_RULE_NOT_FOUND", "Recurring rule was not found"));
        rule.disable();
    }

    public TransactionResponse recordDue(UUID userId, UUID ruleId, Instant now) {
        return executionService.recordDueRule(userId, ruleId, now);
    }

    public int processDueAutoRecords(Instant now) {
        return recurringRuleRepository.findDueAutoRecordIds(now).stream()
                .mapToInt(ruleId -> executionService.autoRecordDueRule(ruleId, now) ? 1 : 0)
                .sum();
    }

    private RecurringRule createRule(UserAccount user, UUID userId, CreateRecurringRuleRequest request) {
        Wallet wallet = walletService.requireOwnedActiveWallet(userId, request.walletId());
        Category category = categoryService.requireAvailableForTransaction(userId, request.categoryId(), categoryTypeFor(request.transactionType()));
        validateRequest(request.transactionType(), request.amount(), wallet.getCurrency(), request.applyAllocationRule());
        return new RecurringRule(
                user,
                wallet,
                category,
                request.name().trim(),
                request.transactionType(),
                normalizeAmount(request.amount(), wallet.getCurrency()),
                wallet.getCurrency(),
                request.description().trim(),
                normalizeNotes(request.notes()),
                request.frequency(),
                request.nextRunAt(),
                request.autoRecord(),
                request.enabled(),
                request.applyAllocationRule());
    }

    private void validateRequest(TransactionType transactionType, BigDecimal amount, String currency, boolean applyAllocationRule) {
        if (transactionType != TransactionType.INCOME && transactionType != TransactionType.EXPENSE) {
            throw new IllegalArgumentException("Recurring rules only support INCOME and EXPENSE transactions");
        }
        if (applyAllocationRule && transactionType != TransactionType.INCOME) {
            throw new IllegalArgumentException("Income allocation can only be applied to recurring income");
        }
        normalizeAmount(amount, currency);
    }

    private CategoryType categoryTypeFor(TransactionType transactionType) {
        return transactionType == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
    }

    private BigDecimal normalizeAmount(BigDecimal requestedAmount, String currencyCode) {
        int scale = Math.max(Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)).getDefaultFractionDigits(), 0);
        if (requestedAmount.scale() > scale) {
            throw new IllegalArgumentException("Amount has more decimal places than the wallet currency supports");
        }
        return requestedAmount.setScale(scale);
    }

    private String normalizeNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }
}
