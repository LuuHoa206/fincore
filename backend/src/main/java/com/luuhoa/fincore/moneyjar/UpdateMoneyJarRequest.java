package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record UpdateMoneyJarRequest(
        @Size(max = 100) String name,
        @DecimalMin(value = "0.00") BigDecimal spendingLimit,
        @Size(max = 20) String color,
        @Size(max = 50) String icon,
        Boolean allowNegative) {
}
