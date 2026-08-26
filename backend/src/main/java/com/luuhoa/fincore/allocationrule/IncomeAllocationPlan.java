package com.luuhoa.fincore.allocationrule;

import java.math.BigDecimal;
import java.util.List;

import com.luuhoa.fincore.moneyjar.MoneyJar;
import com.luuhoa.fincore.wallet.Wallet;

public record IncomeAllocationPlan(
        Wallet wallet,
        List<MoneyJar> lockedJars,
        List<PlannedAllocation> allocations,
        BigDecimal allocatedAmount) {

    public record PlannedAllocation(MoneyJar jar, BigDecimal percentage, BigDecimal amount) {
    }
}
