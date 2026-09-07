package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;

public record JarAllocationResponse(
        MoneyJarResponse jar,
        BigDecimal availableToAllocate) {
}
