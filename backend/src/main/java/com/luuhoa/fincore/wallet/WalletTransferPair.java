package com.luuhoa.fincore.wallet;

/**
 * Two owned, active wallets locked for one internal transfer.
 */
public record WalletTransferPair(Wallet source, Wallet destination) {
}
