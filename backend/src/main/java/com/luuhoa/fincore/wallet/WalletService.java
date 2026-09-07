package com.luuhoa.fincore.wallet;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserAccountRepository userRepository;
    private final AuditLogService auditLogService;

    public WalletService(WalletRepository walletRepository, UserAccountRepository userRepository, AuditLogService auditLogService) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
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

    /**
     * Keeps wallet ownership and row-locking behind the wallet service boundary.
     */
    @Transactional
    public Wallet requireOwnedWalletForTransaction(UUID userId, UUID walletId) {
        return walletRepository.findOwnedForUpdate(walletId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet was not found"));
    }

    /**
     * Locks the two selected rows in database order so simultaneous transfers
     * cannot deadlock by locking source and destination in opposite directions.
     */
    @Transactional
    public WalletTransferPair lockOwnedWalletsForTransfer(UUID userId, UUID sourceWalletId, UUID destinationWalletId) {
        if (sourceWalletId.equals(destinationWalletId)) {
            throw new IllegalArgumentException("Source and destination wallets must be different");
        }
        List<Wallet> wallets = walletRepository.findOwnedActiveByIdsForUpdate(
                userId,
                List.of(sourceWalletId, destinationWalletId));
        if (wallets.size() != 2) {
            throw new ResourceNotFoundException("WALLET_NOT_FOUND", "One or both wallets were not found");
        }
        Wallet source = wallets.stream()
                .filter(wallet -> wallet.getId().equals(sourceWalletId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Source wallet was not found"));
        Wallet destination = wallets.stream()
                .filter(wallet -> wallet.getId().equals(destinationWalletId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Destination wallet was not found"));
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ConflictException("WALLET_CURRENCY_MISMATCH", "Wallets must use the same currency for an internal transfer");
        }
        return new WalletTransferPair(source, destination);
    }

    @Transactional(readOnly = true)
    public Wallet requireOwnedActiveWallet(UUID userId, UUID walletId) {
        return requireOwnedWallet(userId, walletId);
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
            Wallet saved = walletRepository.saveAndFlush(wallet);
            auditLogService.record(user, "WALLET_CREATED", "WALLET", saved.getId(), walletDetails(saved));
            return WalletResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("WALLET_NAME_ALREADY_EXISTS", "A wallet with this name already exists");
        }
    }

    @Transactional
    public WalletResponse update(UUID userId, UUID walletId, UpdateWalletRequest request) {
        Wallet wallet = requireOwnedWallet(userId, walletId);
        UserAccount user = requireUser(userId);
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
            auditLogService.record(user, "WALLET_UPDATED", "WALLET", wallet.getId(), walletDetails(wallet));
            return WalletResponse.from(wallet);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("WALLET_NAME_ALREADY_EXISTS", "A wallet with this name already exists");
        }
    }

    @Transactional
    public void archive(UUID userId, UUID walletId) {
        Wallet wallet = requireOwnedWallet(userId, walletId);
        wallet.archive();
        auditLogService.record(requireUser(userId), "WALLET_ARCHIVED", "WALLET", wallet.getId(), walletDetails(wallet));
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

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private java.util.Map<String, Object> walletDetails(Wallet wallet) {
        return java.util.Map.of(
                "name", wallet.getName(),
                "walletType", wallet.getWalletType().name(),
                "currency", wallet.getCurrency(),
                "allowNegative", wallet.isAllowNegative());
    }
}
