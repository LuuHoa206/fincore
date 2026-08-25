package com.luuhoa.fincore.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        String name,
        WalletType walletType,
        String currency,
        BigDecimal currentBalance,
        boolean allowNegative,
        Instant createdAt,
        Instant updatedAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getName(),
                wallet.getWalletType(),
                wallet.getCurrency(),
                wallet.getCurrentBalance(),
                wallet.isAllowNegative(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt());
    }
}
