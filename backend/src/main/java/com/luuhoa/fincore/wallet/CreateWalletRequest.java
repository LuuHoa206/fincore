package com.luuhoa.fincore.wallet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull WalletType walletType,
        @NotBlank @Size(min = 3, max = 3) String currency,
        boolean allowNegative) {
}
