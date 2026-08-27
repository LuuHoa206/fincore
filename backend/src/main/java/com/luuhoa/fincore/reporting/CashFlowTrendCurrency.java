package com.luuhoa.fincore.reporting;

import java.util.List;

public record CashFlowTrendCurrency(
        String currency,
        List<MonthlyCashFlowTrendPoint> points) {
}
