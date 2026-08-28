package com.luuhoa.fincore.moneyjar;

import java.math.BigDecimal;

public record JarTransferResponse(
        MoneyJarResponse sourceJar,
        MoneyJarResponse destinationJar,
        BigDecimal amount) {
}
