package com.luuhoa.fincore.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.TransactionReportingService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private static final int DEFAULT_WARNING_THRESHOLD = 80;

    private final BudgetRepository budgetRepository;
    private final UserAccountRepository userRepository;
    private final CategoryService categoryService;
    private final TransactionReportingService transactionReportingService;

    public BudgetService(
            BudgetRepository budgetRepository,
            UserAccountRepository userRepository,
            CategoryService categoryService,
            TransactionReportingService transactionReportingService) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
        this.transactionReportingService = transactionReportingService;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(UUID userId, YearMonth requestedPeriod) {
        UserAccount user = requireUser(userId);
        YearMonth period = requestedPeriod == null ? YearMonth.now(ZoneId.of(user.getTimeZone())) : requestedPeriod;
        LocalDate periodStart = period.atDay(1);
        List<Budget> budgets = budgetRepository.findAllByUserIdAndPeriodStartAndArchivedFalseOrderByCreatedAtAsc(userId, periodStart);
        if (budgets.isEmpty()) {
            return List.of();
        }

        Instant from = periodStart.atStartOfDay(ZoneId.of(user.getTimeZone())).toInstant();
        Instant to = period.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of(user.getTimeZone())).toInstant();
        Map<String, List<Budget>> budgetsByCurrency = budgets.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Budget::getCurrency,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Map<UUID, BigDecimal> spentByCategory = new java.util.HashMap<>();
        budgetsByCurrency.values().forEach(group -> spentByCategory.putAll(toSpentByCategory(userId, group, from, to)));

        return budgets.stream()
                .map(budget -> toResponse(budget, spentByCategory.getOrDefault(budget.getCategory().getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public BudgetResponse create(UUID userId, CreateBudgetRequest request) {
        LocalDate periodStart = validatePeriodStart(request.periodStart());
        String currency = normalizeCurrency(request.currency());
        BigDecimal limitAmount = normalizeAmount(request.limitAmount(), currency);
        if (budgetRepository.existsByUserIdAndCategoryIdAndPeriodStartAndArchivedFalse(userId, request.categoryId(), periodStart)) {
            throw duplicateBudget();
        }

        UserAccount user = requireUser(userId);
        Category category = categoryService.requireAvailableForTransaction(userId, request.categoryId(), CategoryType.EXPENSE);
        int warningThreshold = request.warningThreshold() == null ? DEFAULT_WARNING_THRESHOLD : request.warningThreshold();
        Budget budget = new Budget(user, category, periodStart, limitAmount, currency, warningThreshold);
        try {
            Budget savedBudget = budgetRepository.saveAndFlush(budget);
            return toResponse(savedBudget, BigDecimal.ZERO);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateBudget();
        }
    }

    @Transactional
    public BudgetResponse update(UUID userId, UUID budgetId, UpdateBudgetRequest request) {
        Budget budget = requireOwnedBudget(userId, budgetId);
        BigDecimal limitAmount = request.limitAmount() == null ? null : normalizeAmount(request.limitAmount(), budget.getCurrency());
        budget.updatePlan(limitAmount, request.warningThreshold());
        BigDecimal spent = spentForBudget(userId, budget, resolveZone(userId));
        return toResponse(budget, spent);
    }

    @Transactional
    public void archive(UUID userId, UUID budgetId) {
        requireOwnedBudget(userId, budgetId).archive();
    }

    private List<BudgetResponse> toResponses(UUID userId, List<Budget> budgets, Instant from, Instant to) {
        String currency = budgets.getFirst().getCurrency();
        Map<UUID, BigDecimal> amounts = toSpentByCategory(userId, budgets, from, to);
        return budgets.stream()
                .map(budget -> toResponse(budget, amounts.getOrDefault(budget.getCategory().getId(), BigDecimal.ZERO)))
                .toList();
    }

    private Map<UUID, BigDecimal> toSpentByCategory(UUID userId, List<Budget> budgets, Instant from, Instant to) {
        String currency = budgets.getFirst().getCurrency();
        return transactionReportingService.postedExpensesByCategory(
                userId,
                budgets.stream().map(budget -> budget.getCategory().getId()).toList(),
                currency,
                from,
                to);
    }

    private BigDecimal spentForBudget(UUID userId, Budget budget, ZoneId zoneId) {
        YearMonth period = YearMonth.from(budget.getPeriodStart());
        Instant from = period.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant to = period.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();
        return transactionReportingService.postedExpensesByCategory(
                userId,
                List.of(budget.getCategory().getId()),
                budget.getCurrency(),
                from,
                to).getOrDefault(budget.getCategory().getId(), BigDecimal.ZERO);
    }

    private BudgetResponse toResponse(Budget budget, BigDecimal spentAmount) {
        BigDecimal remaining = budget.getLimitAmount().subtract(spentAmount);
        BigDecimal usage = spentAmount.multiply(BigDecimal.valueOf(100))
                .divide(budget.getLimitAmount(), 2, RoundingMode.HALF_UP);
        BudgetStatus status = usage.compareTo(BigDecimal.valueOf(100)) >= 0
                ? BudgetStatus.EXCEEDED
                : usage.compareTo(BigDecimal.valueOf(budget.getWarningThreshold())) >= 0
                        ? BudgetStatus.WARNING
                        : BudgetStatus.ON_TRACK;
        Category category = budget.getCategory();
        return new BudgetResponse(
                budget.getId(),
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getColor(),
                budget.getPeriodStart(),
                budget.getLimitAmount(),
                spentAmount,
                remaining,
                usage,
                budget.getCurrency(),
                budget.getWarningThreshold(),
                status,
                budget.getCreatedAt(),
                budget.getUpdatedAt());
    }

    private Budget requireOwnedBudget(UUID userId, UUID budgetId) {
        return budgetRepository.findByIdAndUserIdAndArchivedFalse(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("BUDGET_NOT_FOUND", "Budget was not found"));
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private ZoneId resolveZone(UUID userId) {
        return ZoneId.of(requireUser(userId).getTimeZone());
    }

    private LocalDate validatePeriodStart(LocalDate periodStart) {
        if (periodStart.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("Budget periodStart must be the first day of a month");
        }
        return periodStart;
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

    private BigDecimal normalizeAmount(BigDecimal requestedAmount, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        int allowedScale = Math.max(currency.getDefaultFractionDigits(), 0);
        if (requestedAmount.scale() > allowedScale) {
            throw new IllegalArgumentException("Amount has more decimal places than the budget currency supports");
        }
        return requestedAmount.setScale(allowedScale);
    }

    private ConflictException duplicateBudget() {
        return new ConflictException("BUDGET_ALREADY_EXISTS", "A budget for this category and month already exists");
    }
}
