package com.luuhoa.fincore.allocationrule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.moneyjar.MoneyJar;
import com.luuhoa.fincore.moneyjar.MoneyJarService;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.FinancialTransaction;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllocationRuleService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final AllocationRuleRepository allocationRuleRepository;
    private final UserAccountRepository userRepository;
    private final MoneyJarService moneyJarService;
    private final WalletService walletService;
    private final AuditLogService auditLogService;

    public AllocationRuleService(
            AllocationRuleRepository allocationRuleRepository,
            UserAccountRepository userRepository,
            MoneyJarService moneyJarService,
            WalletService walletService,
            AuditLogService auditLogService) {
        this.allocationRuleRepository = allocationRuleRepository;
        this.userRepository = userRepository;
        this.moneyJarService = moneyJarService;
        this.walletService = walletService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AllocationRuleResponse> list(UUID userId) {
        return allocationRuleRepository.findAllByUserIdWithItems(userId).stream()
                .map(AllocationRuleResponse::from)
                .toList();
    }

    @Transactional
    public AllocationRuleResponse create(UUID userId, CreateAllocationRuleRequest request) {
        UserAccount user = requireUser(userId);
        String currency = normalizeCurrency(request.currency());
        String name = normalizeName(request.name());
        if (allocationRuleRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("ALLOCATION_RULE_NAME_ALREADY_EXISTS", "An allocation rule with this name already exists");
        }
        AllocationRule rule = new AllocationRule(user, name, currency, request.enabled());
        rule.replaceItems(buildItems(userId, currency, request.items()));
        AllocationRule savedRule;
        try {
            savedRule = allocationRuleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEnabledRule(exception, request.enabled());
        }
        auditLogService.record(user, "ALLOCATION_RULE_CREATED", "ALLOCATION_RULE", savedRule.getId(), ruleDetails(savedRule));
        return AllocationRuleResponse.from(savedRule);
    }

    @Transactional
    public AllocationRuleResponse update(UUID userId, UUID ruleId, UpdateAllocationRuleRequest request) {
        AllocationRule rule = requireOwnedRule(userId, ruleId);
        UserAccount user = requireUser(userId);
        String name = normalizeName(request.name());
        if (!rule.getName().equalsIgnoreCase(name) && allocationRuleRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("ALLOCATION_RULE_NAME_ALREADY_EXISTS", "An allocation rule with this name already exists");
        }
        rule.update(name, request.enabled());
        rule.replaceItems(buildItems(userId, rule.getCurrency(), request.items()));
        try {
            allocationRuleRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEnabledRule(exception, request.enabled());
        }
        auditLogService.record(user, "ALLOCATION_RULE_UPDATED", "ALLOCATION_RULE", rule.getId(), ruleDetails(rule));
        return AllocationRuleResponse.from(rule);
    }

    @Transactional
    public void delete(UUID userId, UUID ruleId) {
        AllocationRule rule = requireOwnedRule(userId, ruleId);
        allocationRuleRepository.delete(rule);
        auditLogService.record(requireUser(userId), "ALLOCATION_RULE_DELETED", "ALLOCATION_RULE", rule.getId(), ruleDetails(rule));
    }

    @Transactional(readOnly = true)
    public AllocationPreviewResponse previewIncomeAllocation(UUID userId, UUID walletId, BigDecimal amount) {
        Wallet wallet = walletService.requireOwnedActiveWallet(userId, walletId);
        BigDecimal normalizedAmount = normalizeAmount(amount, wallet.getCurrency());
        AllocationRule rule = allocationRuleRepository.findEnabledByUserIdAndCurrencyAndEnabledTrue(userId, wallet.getCurrency())
                .orElseThrow(() -> new ConflictException("ALLOCATION_RULE_NOT_FOUND", "No active allocation rule exists for this wallet currency"));
        return toPreview(rule, normalizedAmount);
    }

    /**
     * The transaction service calls this before applying an income. Wallets and jars are
     * locked in the same stable order as manual jar allocation, then the active rule is
     * locked before its percentages are used.
     */
    @Transactional
    public IncomeAllocationPlan prepareIncomeAllocation(UUID userId, UUID walletId, BigDecimal amount) {
        List<Wallet> wallets = walletService.lockActiveWalletsForAllocation(userId);
        Wallet wallet = wallets.stream()
                .filter(candidate -> candidate.getId().equals(walletId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet was not found"));
        List<MoneyJar> jars = moneyJarService.lockActiveJarsForAllocation(userId);
        AllocationRule rule = allocationRuleRepository.findEnabledForUpdate(userId, wallet.getCurrency())
                .orElseThrow(() -> new ConflictException("ALLOCATION_RULE_NOT_FOUND", "No active allocation rule exists for this wallet currency"));
        BigDecimal normalizedAmount = normalizeAmount(amount, wallet.getCurrency());
        Map<UUID, MoneyJar> jarsById = jars.stream().collect(Collectors.toMap(MoneyJar::getId, jar -> jar));
        List<IncomeAllocationPlan.PlannedAllocation> allocations = calculateAllocations(rule, jarsById, normalizedAmount);
        BigDecimal allocatedAmount = allocations.stream().map(IncomeAllocationPlan.PlannedAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new IncomeAllocationPlan(wallet, jars, allocations, allocatedAmount);
    }

    @Transactional
    public void applyIncomeAllocation(IncomeAllocationPlan plan, FinancialTransaction transaction) {
        List<MoneyJarService.AutomaticJarAllocation> allocations = plan.allocations().stream()
                .filter(item -> item.amount().signum() > 0)
                .map(item -> new MoneyJarService.AutomaticJarAllocation(item.jar().getId(), item.amount()))
                .toList();
        if (!allocations.isEmpty()) {
            moneyJarService.applyAutomaticAllocations(plan.lockedJars(), transaction, allocations);
        }
    }

    private AllocationPreviewResponse toPreview(AllocationRule rule, BigDecimal amount) {
        Map<UUID, MoneyJar> jarsById = rule.getItems().stream()
                .map(AllocationRuleItem::getJar)
                .collect(Collectors.toMap(MoneyJar::getId, jar -> jar));
        List<IncomeAllocationPlan.PlannedAllocation> allocations = calculateAllocations(rule, jarsById, amount);
        List<AllocationPreviewResponse.Item> items = allocations.stream()
                .map(item -> new AllocationPreviewResponse.Item(item.jar().getId(), item.jar().getName(), item.percentage(), item.amount()))
                .toList();
        BigDecimal allocatedAmount = items.stream().map(AllocationPreviewResponse.Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AllocationPreviewResponse(rule.getId(), rule.getName(), rule.getCurrency(), amount, allocatedAmount, amount.subtract(allocatedAmount), items);
    }

    private List<IncomeAllocationPlan.PlannedAllocation> calculateAllocations(
            AllocationRule rule,
            Map<UUID, MoneyJar> jarsById,
            BigDecimal amount) {
        int scale = Math.max(Currency.getInstance(rule.getCurrency()).getDefaultFractionDigits(), 0);
        return rule.getItems().stream().map(item -> {
            MoneyJar jar = jarsById.get(item.getJar().getId());
            if (jar == null || !jar.getCurrency().equals(rule.getCurrency())) {
                throw new ConflictException("ALLOCATION_JAR_UNAVAILABLE", "A money jar in this allocation rule is no longer available");
            }
            BigDecimal allocated = amount.multiply(item.getPercentage()).divide(ONE_HUNDRED, scale, RoundingMode.DOWN);
            return new IncomeAllocationPlan.PlannedAllocation(jar, item.getPercentage(), allocated);
        }).toList();
    }

    private List<AllocationRuleItem> buildItems(UUID userId, String currency, List<AllocationRuleItemRequest> requests) {
        Set<UUID> jarIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        List<AllocationRuleItem> items = new ArrayList<>();
        for (AllocationRuleItemRequest request : requests) {
            if (!jarIds.add(request.jarId())) {
                throw new IllegalArgumentException("A money jar can only appear once in an allocation rule");
            }
            BigDecimal percentage = request.percentage().setScale(2, RoundingMode.UNNECESSARY);
            total = total.add(percentage);
            MoneyJar jar = moneyJarService.requireOwnedActiveJarForSavingGoal(userId, request.jarId());
            if (!currency.equals(jar.getCurrency())) {
                throw new IllegalArgumentException("All money jars in an allocation rule must use the rule currency");
            }
            items.add(new AllocationRuleItem(jar, percentage));
        }
        if (total.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Allocation percentages must not exceed 100");
        }
        return items;
    }

    private AllocationRule requireOwnedRule(UUID userId, UUID ruleId) {
        return allocationRuleRepository.findOwnedWithItems(ruleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ALLOCATION_RULE_NOT_FOUND", "Allocation rule was not found"));
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private Map<String, Object> ruleDetails(AllocationRule rule) {
        return Map.of(
                "name", rule.getName(),
                "currency", rule.getCurrency(),
                "enabled", rule.isEnabled(),
                "itemCount", rule.getItems().size());
    }

    private BigDecimal normalizeAmount(BigDecimal requestedAmount, String currencyCode) {
        if (requestedAmount == null || requestedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        int scale = Math.max(Currency.getInstance(currencyCode).getDefaultFractionDigits(), 0);
        if (requestedAmount.scale() > scale) {
            throw new IllegalArgumentException("Amount has more decimal places than the wallet currency supports");
        }
        return requestedAmount.setScale(scale);
    }

    private String normalizeCurrency(String requestedCurrency) {
        String currency = requestedCurrency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency must be a valid ISO 4217 code");
        }
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private ConflictException duplicateEnabledRule(DataIntegrityViolationException exception, boolean enabled) {
        return new ConflictException("ALLOCATION_RULE_ALREADY_ACTIVE", "Only one active allocation rule is allowed for each currency");
    }
}
