package com.luuhoa.fincore.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategorySuggestionServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategorySuggestionService service;

    @BeforeEach
    void setUp() {
        service = new CategorySuggestionService(categoryRepository);
    }

    @Test
    void suggestsTheMatchingVisibleCategoryAndExplainsWhy() {
        UUID userId = UUID.randomUUID();
        Category food = new Category(null, "An uong", CategoryType.EXPENSE, "utensils", "#EA580C");
        Category transport = new Category(null, "Di chuyen", CategoryType.EXPENSE, "car-front", "#2563EB");
        when(categoryRepository.findVisibleByUserId(userId, CategoryType.EXPENSE))
                .thenReturn(List.of(transport, food));

        List<CategorySuggestionResponse> suggestions = service.suggest(userId, CategoryType.EXPENSE, "Ăn cơm và uống cà phê");

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().categoryName()).isEqualTo("An uong");
        assertThat(suggestions.getFirst().reason()).contains("com", "ca phe");
        assertThat(suggestions.getFirst().score()).isGreaterThan(0);
    }

    @Test
    void supportsAVisibleCustomCategoryThroughNameMatching() {
        UUID userId = UUID.randomUUID();
        Category petCare = new Category(null, "Pet care", CategoryType.EXPENSE, "paw-print", "#7C3AED");
        when(categoryRepository.findVisibleByUserId(userId, CategoryType.EXPENSE)).thenReturn(List.of(petCare));

        List<CategorySuggestionResponse> suggestions = service.suggest(userId, CategoryType.EXPENSE, "Pet food cho mèo");

        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.categoryName()).isEqualTo("Pet care");
            assertThat(suggestion.reason()).contains("pet");
        });
    }

    @Test
    void rejectsBlankDescriptionBeforeLoadingCategories() {
        assertThatThrownBy(() -> service.suggest(UUID.randomUUID(), CategoryType.EXPENSE, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");

        verifyNoInteractions(categoryRepository);
    }
}
