package com.luuhoa.fincore.wallet;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserAccountRepository userRepository;

    public WalletService(WalletRepository walletRepository, UserAccountRepository userRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> list(UUID userId) {
        return walletRepository.findAllByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId)
                .stream()
                .map(WalletResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WalletResponse get(UUID userId, UUID walletId) {
        return WalletResponse.from(requireOwnedWallet(userId, walletId));
    }

    /**
     * Internal service boundary for use cases that need a consistent allocation snapshot.
     */
    @Transactional
    public List<Wallet> lockActiveWalletsForAllocation(UUID userId) {
        return walletRepository.findAllActiveByUserIdForUpdate(userId);
    }

    @Transactional
    public WalletResponse create(UUID userId, CreateWalletRequest request) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        String currency = normalizeCurrency(request.currency());
        Wallet wallet = new Wallet(
                user,
                request.name().trim(),
                request.walletType(),
                currency,
                request.allowNegative());
        try {
            return WalletResponse.from(walletRepository.saveAndFlush(wallet));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("WALLET_NAME_ALREADY_EXISTS", "A wallet with this name already exists");
        }
    }

    @Transactional
    public WalletResponse update(UUID userId, UUID walletId, UpdateWalletRequest request) {
        Wallet wallet = requireOwnedWallet(userId, walletId);
        String name = request.name();
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Wallet name must not be blank");
            }
        }
        wallet.updateDetails(name, request.walletType(), request.allowNegative());
        try {
            walletRepository.flush();
            return WalletResponse.from(wallet);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("WALLET_NAME_ALREADY_EXISTS", "A wallet with this name already exists");
        }
    }

    @Transactional
    public void archive(UUID userId, UUID walletId) {
        requireOwnedWallet(userId, walletId).archive();
    }

    private Wallet requireOwnedWallet(UUID userId, UUID walletId) {
        return walletRepository.findByIdAndUserIdAndArchivedFalse(walletId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet was not found"));
    }

    private String normalizeCurrency(String requestedCurrency) {
        String currency = requestedCurrency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency must be a valid ISO 4217 code");
        }
    }
}
