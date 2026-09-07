package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ChangeJarAllocationRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
}
