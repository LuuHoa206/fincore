package com.luuhoa.fincore.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserAccountRepository userRepository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, userRepository);
    }

    @Test
    void listsSystemAndUserCategoriesVisibleToTheOwner() {
        UUID userId = UUID.randomUUID();
        UserAccount user = user();
        Category systemCategory = new Category(null, "Food", CategoryType.EXPENSE, "utensils", "#EA580C");
        Category customCategory = new Category(user, "Pet care", CategoryType.EXPENSE, "paw-print", "#7C3AED");
        when(categoryRepository.findVisibleByUserId(userId, CategoryType.EXPENSE))
                .thenReturn(List.of(customCategory, systemCategory));

        List<CategoryResponse> categories = service.list(userId, CategoryType.EXPENSE);

        assertThat(categories).hasSize(2);
        assertThat(categories).extracting(CategoryResponse::name).containsExactly("Pet care", "Food");
        assertThat(categories).extracting(CategoryResponse::systemCategory).containsExactly(false, true);
    }

    @Test
    void preventsDuplicateUserCategoryNamesForTheSameType() {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("Food", CategoryType.EXPENSE, "utensils", "#EA580C");
        when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndCategoryType(userId, "Food", CategoryType.EXPENSE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).findById(any());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void rejectsCategoryWithTheWrongTransactionType() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category incomeCategory = new Category(null, "Salary", CategoryType.INCOME, "banknote", "#0F8F72");
        when(categoryRepository.findAvailableByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(incomeCategory));

        assertThatThrownBy(() -> service.requireAvailableForTransaction(userId, categoryId, CategoryType.EXPENSE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("does not match");
    }

    private UserAccount user() {
        return new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
    }
}
