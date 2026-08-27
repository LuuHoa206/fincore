package com.luuhoa.fincore.reporting;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyCashFlowTrendPoint(
        YearMonth period,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net) {
}
