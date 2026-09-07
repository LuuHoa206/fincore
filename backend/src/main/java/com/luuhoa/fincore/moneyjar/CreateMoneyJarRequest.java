package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMoneyJarRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @DecimalMin(value = "0.00") BigDecimal spendingLimit,
        @Size(max = 20) String color,
        @Size(max = 50) String icon,
        boolean allowNegative) {
}
