package com.luuhoa.fincore.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.category.CategoryResponse;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.transaction.TransactionReportingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetLimitSuggestionServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private UserAccountRepository userRepository;
    @Mock private CategoryService categoryService;
    @Mock private TransactionReportingService transactionReportingService;

    private BudgetLimitSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new BudgetLimitSuggestionService(
                budgetRepository, userRepository, categoryService, transactionReportingService);
    }

    @Test
    void suggestsAverageOfThreePriorMonthsForEligibleExpenseCategories() {
        UUID userId = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();
        UUID transportId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(budgetRepository.findAllByUserIdAndPeriodStartAndArchivedFalseOrderByCreatedAtAsc(
                userId, LocalDate.of(2026, 8, 1))).thenReturn(List.of());
        when(categoryService.list(userId, CategoryType.EXPENSE)).thenReturn(List.of(
                category(foodId, "Food"), category(transportId, "Transport")));
        when(transactionReportingService.postedExpensesByCategory(
                eq(userId), eq(List.of(foodId, transportId)), eq("VND"), any(Instant.class), any(Instant.class)))
                .thenReturn(Map.of(foodId, new BigDecimal("900000"), transportId, new BigDecimal("1200000")));

        List<BudgetLimitSuggestionResponse> result = service.list(userId, YearMonth.of(2026, 8), "vnd");

        assertThat(result).extracting(BudgetLimitSuggestionResponse::categoryName).containsExactly("Transport", "Food");
        assertThat(result).extracting(BudgetLimitSuggestionResponse::suggestedLimit)
                .containsExactly(new BigDecimal("400000"), new BigDecimal("300000"));
        assertThat(result).allSatisfy(suggestion -> {
            assertThat(suggestion.historyMonths()).isEqualTo(3);
            assertThat(suggestion.periodStart()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(suggestion.reason()).contains("3 tháng trước");
        });
    }

    @Test
    void excludesCategoriesThatAlreadyHaveABudgetForTheSelectedMonth() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID budgetedCategoryId = UUID.randomUUID();
        UUID eligibleCategoryId = UUID.randomUUID();
        Budget budget = org.mockito.Mockito.mock(Budget.class);
        com.luuhoa.fincore.category.Category category = org.mockito.Mockito.mock(com.luuhoa.fincore.category.Category.class);
        when(category.getId()).thenReturn(budgetedCategoryId);
        when(budget.getCategory()).thenReturn(category);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(budgetRepository.findAllByUserIdAndPeriodStartAndArchivedFalseOrderByCreatedAtAsc(
                userId, LocalDate.of(2026, 8, 1))).thenReturn(List.of(budget));
        when(categoryService.list(userId, CategoryType.EXPENSE)).thenReturn(List.of(
                category(budgetedCategoryId, "Food"), category(eligibleCategoryId, "Transport")));
        when(transactionReportingService.postedExpensesByCategory(
                eq(userId), eq(List.of(eligibleCategoryId)), eq("VND"), any(Instant.class), any(Instant.class)))
                .thenReturn(Map.of(eligibleCategoryId, new BigDecimal("300000")));

        List<BudgetLimitSuggestionResponse> result = service.list(userId, YearMonth.of(2026, 8), "VND");

        assertThat(result).singleElement().extracting(BudgetLimitSuggestionResponse::categoryId).isEqualTo(eligibleCategoryId);
        verify(transactionReportingService).postedExpensesByCategory(
                eq(userId), eq(List.of(eligibleCategoryId)), eq("VND"), any(Instant.class), any(Instant.class));
    }

    private UserAccount user() {
        return new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
    }

    private CategoryResponse category(UUID id, String name) {
        return new CategoryResponse(id, name, CategoryType.EXPENSE, "circle", "#0F8F72", false, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
