package com.luuhoa.fincore.category;

import java.util.UUID;

/** A transparent category recommendation. The client must still make the final selection. */
public record CategorySuggestionResponse(
        UUID categoryId,
        String categoryName,
        CategoryType categoryType,
        boolean systemCategory,
        String reason,
        int score) {
}
