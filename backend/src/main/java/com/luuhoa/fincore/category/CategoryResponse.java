package com.luuhoa.fincore.category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType categoryType,
        String icon,
        String color,
        boolean systemCategory,
        Instant createdAt) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getCategoryType(),
                category.getIcon(),
                category.getColor(),
                category.isSystemCategory(),
                category.getCreatedAt());
    }
}
