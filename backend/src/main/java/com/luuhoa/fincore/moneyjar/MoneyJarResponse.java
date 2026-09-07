package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MoneyJarResponse(
        UUID id,
        String name,
        String currency,
        BigDecimal allocatedBalance,
        BigDecimal spendingLimit,
        String color,
        String icon,
        boolean allowNegative,
        Instant createdAt,
        Instant updatedAt) {

    public static MoneyJarResponse from(MoneyJar jar) {
        return new MoneyJarResponse(
                jar.getId(),
                jar.getName(),
                jar.getCurrency(),
                jar.getAllocatedBalance(),
                jar.getSpendingLimit(),
                jar.getColor(),
                jar.getIcon(),
                jar.isAllowNegative(),
                jar.getCreatedAt(),
                jar.getUpdatedAt());
    }
}
