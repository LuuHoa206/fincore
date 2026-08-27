package com.luuhoa.fincore.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.transaction.TransactionReportingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private TransactionReportingService transactionReportingService;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetRepository, userRepository, categoryService, transactionReportingService);
    }

    @Test
    void listsBudgetsUsingPostedExpenseTotalsInsteadOfStoredSpend() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UserAccount user = user();
        Category category = category(user, categoryId, "Food");
        Budget budget = budget(user, category, new BigDecimal("1000000"), 80);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(budgetRepository.findAllByUserIdAndPeriodStartAndArchivedFalseOrderByCreatedAtAsc(
                userId, LocalDate.of(2026, 8, 1))).thenReturn(List.of(budget));
        when(transactionReportingService.postedExpensesByCategory(
                eq(userId), eq(List.of(categoryId)), eq("VND"), any(Instant.class), any(Instant.class)))
                .thenReturn(Map.of(categoryId, new BigDecimal("850000")));

        List<BudgetResponse> result = service.list(userId, YearMonth.of(2026, 8));

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.spentAmount()).isEqualByComparingTo("850000");
            assertThat(response.remainingAmount()).isEqualByComparingTo("150000");
            assertThat(response.usagePercentage()).isEqualByComparingTo("85.00");
            assertThat(response.status()).isEqualTo(BudgetStatus.WARNING);
        });
    }

    @Test
    void rejectsDuplicateBudgetBeforeResolvingCategory() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CreateBudgetRequest request = new CreateBudgetRequest(
                categoryId, LocalDate.of(2026, 8, 1), new BigDecimal("1000000"), "VND", 80);

        when(budgetRepository.existsByUserIdAndCategoryIdAndPeriodStartAndArchivedFalse(
                userId, categoryId, request.periodStart())).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(categoryService, never()).requireAvailableForTransaction(any(), any(), any());
    }

    @Test
    void createsExpenseBudgetThroughCategoryService() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UserAccount user = user();
        Category category = category(user, categoryId, "Transport");
        CreateBudgetRequest request = new CreateBudgetRequest(
                categoryId, LocalDate.of(2026, 8, 1), new BigDecimal("500000"), "VND", null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(categoryService.requireAvailableForTransaction(userId, categoryId, CategoryType.EXPENSE)).thenReturn(category);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse response = service.create(userId, request);

        assertThat(response.categoryName()).isEqualTo("Transport");
        assertThat(response.warningThreshold()).isEqualTo(80);
        assertThat(response.status()).isEqualTo(BudgetStatus.ON_TRACK);
        verify(categoryService).requireAvailableForTransaction(userId, categoryId, CategoryType.EXPENSE);
    }

    @Test
    void rejectsPeriodThatDoesNotStartOnFirstDayOfMonth() {
        CreateBudgetRequest request = new CreateBudgetRequest(
                UUID.randomUUID(), LocalDate.of(2026, 8, 2), new BigDecimal("500000"), "VND", 80);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first day");
    }

    @Test
    void refusesToArchiveABudgetOutsideTheCurrentUsersScope() {
        UUID userId = UUID.randomUUID();
        UUID foreignBudgetId = UUID.randomUUID();
        when(budgetRepository.findByIdAndUserIdAndArchivedFalse(foreignBudgetId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(userId, foreignBudgetId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Budget was not found");

        verify(budgetRepository, never()).findById(foreignBudgetId);
    }

    private UserAccount user() {
        return new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
    }

    private Category category(UserAccount user, UUID categoryId, String name) throws Exception {
        Category category = new Category(user, name, CategoryType.EXPENSE, "circle", "#0F8F72");
        setId(category, categoryId);
        return category;
    }

    private Budget budget(UserAccount user, Category category, BigDecimal limit, int threshold) throws Exception {
        Budget budget = new Budget(user, category, LocalDate.of(2026, 8, 1), limit, "VND", threshold);
        setId(budget, UUID.randomUUID());
        return budget;
    }

    private void setId(Object target, UUID id) throws Exception {
        Field idField = target.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }
}
