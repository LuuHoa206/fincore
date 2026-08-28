package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.luuhoa.fincore.category.CategorySuggestionResponse;

/**
 * An explainable draft only. The client must apply and submit the final values
 * through the normal transaction endpoint.
 */
public record TransactionDraftSuggestionResponse(
        String description,
        BigDecimal suggestedAmount,
        TransactionType suggestedTransactionType,
        LocalDate suggestedDate,
        List<String> signals,
        List<CategorySuggestionResponse> categorySuggestions) {
}
