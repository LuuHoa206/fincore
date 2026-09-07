package com.luuhoa.fincore.allocationrule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AllocationRuleResponse(
        UUID id,
        String name,
        String currency,
        boolean enabled,
        BigDecimal totalPercentage,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public static AllocationRuleResponse from(AllocationRule rule) {
        List<Item> items = rule.getItems().stream()
                .map(item -> new Item(item.getJar().getId(), item.getJar().getName(), item.getPercentage()))
                .toList();
        BigDecimal totalPercentage = items.stream().map(Item::percentage).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AllocationRuleResponse(rule.getId(), rule.getName(), rule.getCurrency(), rule.isEnabled(), totalPercentage, items, rule.getCreatedAt(), rule.getUpdatedAt());
    }

    public record Item(UUID jarId, String jarName, BigDecimal percentage) {
    }
}
