package com.luuhoa.fincore.category;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.config.SecurityConfig;
import com.luuhoa.fincore.shared.api.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CategoryControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private CategorySuggestionService categorySuggestionService;

    @Test
    void returnsOnlyTheAuthenticatedUsersCategorySuggestions() throws Exception {
        UUID userId = UUID.randomUUID();
        CategorySuggestionResponse suggestion = new CategorySuggestionResponse(
                UUID.randomUUID(), "An uong", CategoryType.EXPENSE, true, "Khớp nội dung: com", 3);
        when(categorySuggestionService.suggest(userId, CategoryType.EXPENSE, "An com trua"))
                .thenReturn(List.of(suggestion));

        mockMvc.perform(get("/api/v1/categories/suggestions")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .queryParam("type", "EXPENSE")
                        .queryParam("description", "An com trua"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("An uong"))
                .andExpect(jsonPath("$[0].reason").value("Khớp nội dung: com"));

        verify(categorySuggestionService).suggest(eq(userId), eq(CategoryType.EXPENSE), eq("An com trua"));
    }

    @Test
    void protectsSuggestionsLikeEveryOtherFinancialEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/categories/suggestions")
                        .queryParam("type", "EXPENSE")
                        .queryParam("description", "An com trua"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categorySuggestionService);
    }
}
