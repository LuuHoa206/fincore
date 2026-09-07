package com.luuhoa.fincore.allocationrule;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AllocationRuleItemRequest(
        @NotNull UUID jarId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal percentage) {
}
