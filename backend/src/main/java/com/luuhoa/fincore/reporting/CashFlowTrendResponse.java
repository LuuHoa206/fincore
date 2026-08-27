package com.luuhoa.fincore.reporting;

import java.util.List;

public record CashFlowTrendResponse(
        int months,
        String timeZone,
        List<CashFlowTrendCurrency> currencySeries) {
}
