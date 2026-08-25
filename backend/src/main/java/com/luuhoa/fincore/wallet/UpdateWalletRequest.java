package com.luuhoa.fincore.wallet;

import jakarta.validation.constraints.Size;

public record UpdateWalletRequest(
        @Size(max = 100) String name,
        WalletType walletType,
        Boolean allowNegative) {
}
