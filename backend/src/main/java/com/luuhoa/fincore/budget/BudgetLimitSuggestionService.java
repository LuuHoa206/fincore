package com.luuhoa.fincore.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.luuhoa.fincore.category.CategoryResponse;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.TransactionReportingService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetLimitSuggestionService {

    private static final int HISTORY_MONTHS = 3;

    private final BudgetRepository budgetRepository;
    private final UserAccountRepository userRepository;
    private final CategoryService categoryService;
    private final TransactionReportingService transactionReportingService;

    public BudgetLimitSuggestionService(
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
    public List<BudgetLimitSuggestionResponse> list(UUID userId, YearMonth requestedPeriod, String requestedCurrency) {
        UserAccount user = requireUser(userId);
        ZoneId timeZone = ZoneId.of(user.getTimeZone());
        YearMonth period = requestedPeriod == null ? YearMonth.now(timeZone) : requestedPeriod;
        String currency = normalizeCurrency(requestedCurrency == null ? user.getPreferredCurrency() : requestedCurrency);
        LocalDate periodStart = period.atDay(1);

        Set<UUID> budgetedCategoryIds = budgetRepository
                .findAllByUserIdAndPeriodStartAndArchivedFalseOrderByCreatedAtAsc(userId, periodStart).stream()
                .map(budget -> budget.getCategory().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<CategoryResponse> eligibleCategories = categoryService.list(userId, CategoryType.EXPENSE).stream()
                .filter(category -> !budgetedCategoryIds.contains(category.id()))
                .toList();
        if (eligibleCategories.isEmpty()) {
            return List.of();
        }

        YearMonth historyStart = period.minusMonths(HISTORY_MONTHS);
        Instant from = historyStart.atDay(1).atStartOfDay(timeZone).toInstant();
        Instant to = periodStart.atStartOfDay(timeZone).toInstant();
        Map<UUID, BigDecimal> totals = transactionReportingService.postedExpensesByCategory(
                userId,
                eligibleCategories.stream().map(CategoryResponse::id).toList(),
                currency,
                from,
                to);
        int scale = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);

        return eligibleCategories.stream()
                .map(category -> toResponse(category, periodStart, currency, scale, totals.get(category.id())))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(BudgetLimitSuggestionResponse::suggestedLimit).reversed())
                .toList();
    }

    private BudgetLimitSuggestionResponse toResponse(
            CategoryResponse category,
            LocalDate periodStart,
            String currency,
            int scale,
            BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return null;
        }
        BigDecimal suggestedLimit = total.divide(BigDecimal.valueOf(HISTORY_MONTHS), scale, RoundingMode.HALF_UP);
        return new BudgetLimitSuggestionResponse(
                category.id(),
                category.name(),
                category.icon(),
                category.color(),
                periodStart,
                currency,
                HISTORY_MONTHS,
                total,
                suggestedLimit,
                "Trung bình chi tiêu đã ghi nhận trong " + HISTORY_MONTHS + " tháng trước");
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
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
}
