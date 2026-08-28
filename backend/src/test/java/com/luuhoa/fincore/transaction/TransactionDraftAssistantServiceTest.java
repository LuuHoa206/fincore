package com.luuhoa.fincore.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.luuhoa.fincore.category.CategorySuggestionResponse;
import com.luuhoa.fincore.category.CategorySuggestionService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.UserResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionDraftAssistantServiceTest {

    @Mock private AuthService authService;
    @Mock private CategorySuggestionService categorySuggestionService;

    private TransactionDraftAssistantService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TransactionDraftAssistantService(
                authService,
                categorySuggestionService,
                Clock.fixed(Instant.parse("2026-08-28T08:00:00Z"), ZoneOffset.UTC));
        when(authService.currentUser(userId)).thenReturn(new UserResponse(
                userId, "user@example.com", "User", "VND", "Asia/Ho_Chi_Minh", Set.of("USER"), Instant.now()));
    }

    @Test
    void suggestsAnExpenseAmountDateAndVisibleCategoryWithoutWriting() {
        CategorySuggestionResponse category = new CategorySuggestionResponse(
                UUID.randomUUID(), "An uong", CategoryType.EXPENSE, true, "Khop noi dung: cafe", 3);
        when(categorySuggestionService.suggest(eq(userId), eq(CategoryType.EXPENSE), eq("Ca phe 45k hom nay")))
                .thenReturn(List.of(category));

        TransactionDraftSuggestionResponse result = service.suggest(userId,
                new TransactionDraftSuggestionRequest("Ca phe 45k hom nay", "VND", TransactionType.EXPENSE));

        assertThat(result.suggestedTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(result.suggestedAmount()).isEqualByComparingTo(BigDecimal.valueOf(45_000));
        assertThat(result.suggestedDate()).hasToString("2026-08-28");
        assertThat(result.categorySuggestions()).containsExactly(category);
    }

    @Test
    void understandsDecimalMillionsForVndIncome() {
        when(categorySuggestionService.suggest(eq(userId), eq(CategoryType.INCOME), eq("Nhan thuong 1,5tr hom qua")))
                .thenReturn(List.of());

        TransactionDraftSuggestionResponse result = service.suggest(userId,
                new TransactionDraftSuggestionRequest("Nhan thuong 1,5tr hom qua", "VND", TransactionType.EXPENSE));

        assertThat(result.suggestedTransactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(result.suggestedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_500_000));
        assertThat(result.suggestedDate()).hasToString("2026-08-27");
    }
}
