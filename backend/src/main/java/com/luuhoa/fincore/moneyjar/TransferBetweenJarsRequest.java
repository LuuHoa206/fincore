package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record TransferBetweenJarsRequest(
        @NotNull UUID sourceJarId,
        @NotNull UUID destinationJarId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
}
