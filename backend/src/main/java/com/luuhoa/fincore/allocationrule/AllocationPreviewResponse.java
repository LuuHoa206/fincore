package com.luuhoa.fincore.allocationrule;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AllocationPreviewResponse(
        UUID ruleId,
        String ruleName,
        String currency,
        BigDecimal inputAmount,
        BigDecimal allocatedAmount,
        BigDecimal remainingAmount,
        List<Item> items) {

    public record Item(UUID jarId, String jarName, BigDecimal percentage, BigDecimal amount) {
    }
}
