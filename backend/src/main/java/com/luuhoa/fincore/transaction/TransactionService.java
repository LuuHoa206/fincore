package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class TransactionService {

    private static final String EXTERNAL_ACCOUNT = "External source";

    private final FinancialTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final UserAccountRepository userRepository;
    private final CategoryService categoryService;

    public TransactionService(
            FinancialTransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            WalletRepository walletRepository,
            UserAccountRepository userRepository,
            CategoryService categoryService) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(UUID userId) {
        return transactionRepository.findTop100ByUserIdOrderByOccurredAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listRecent(UUID userId) {
        return transactionRepository.findTop5ByUserIdOrderByOccurredAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse search(
            UUID userId,
            TransactionType transactionType,
            String query,
            Instant fromTime,
            Instant toTime,
            int page,
            int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("size must be between 1 and 50");
        }
        if (fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw new IllegalArgumentException("from must be before to");
        }
        String searchTerm = query == null || query.isBlank() ? null : query.trim();
        var result = transactionRepository.searchByUser(
                userId,
                transactionType,
                searchTerm,
                fromTime,
                toTime,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return new TransactionPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest request, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            FinancialTransaction existing = transactionRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey)
                    .orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        if (request.transactionType() != TransactionType.INCOME && request.transactionType() != TransactionType.EXPENSE) {
            throw new IllegalArgumentException("Only INCOME and EXPENSE are supported when recording a transaction");
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        Wallet wallet = requireOwnedWalletForUpdate(userId, request.walletId());
        Category category = categoryService.requireAvailableForTransaction(
                userId,
                request.categoryId(),
                categoryTypeFor(request.transactionType()));
        BigDecimal amount = validateAndNormalizeAmount(request.amount(), wallet.getCurrency());
        BigDecimal walletChange = request.transactionType() == TransactionType.INCOME ? amount : amount.negate();

        wallet.applyBalance(walletChange);
        FinancialTransaction transaction = new FinancialTransaction(
                user,
                category,
                request.transactionType(),
                amount,
                wallet.getCurrency(),
                request.description().trim(),
                normalizeNotes(request.notes()),
                request.occurredAt(),
                normalizedKey);
        transactionRepository.save(transaction);
        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.walletEntry(transaction, wallet, walletChange),
                LedgerEntry.externalEntry(transaction, EXTERNAL_ACCOUNT, walletChange.negate(), wallet.getCurrency())));

        return TransactionResponse.from(transaction, wallet);
    }

    @Transactional
    public TransactionResponse reverse(UUID userId, UUID transactionId) {
        FinancialTransaction original = transactionRepository.findOwnedForUpdate(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSACTION_NOT_FOUND", "Transaction was not found"));
        if (original.getStatus() != TransactionStatus.POSTED || original.getTransactionType() == TransactionType.REVERSAL
                || transactionRepository.existsByReversedTransactionId(transactionId)) {
            throw new ConflictException("TRANSACTION_CANNOT_BE_REVERSED", "This transaction has already been reversed or cannot be reversed");
        }

        LedgerEntry originalWalletEntry = ledgerEntryRepository.findFirstByTransactionIdAndAccountKind(transactionId, AccountKind.WALLET)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSACTION_LEDGER_NOT_FOUND", "Transaction ledger entry was not found"));
        Wallet originalWallet = originalWalletEntry.getWallet();
        Wallet wallet = requireOwnedWalletForUpdate(userId, originalWallet.getId());
        BigDecimal reversalAmount = originalWalletEntry.getSignedAmount().negate();

        wallet.applyBalance(reversalAmount);
        FinancialTransaction reversal = FinancialTransaction.reversalOf(original, requireUser(userId));
        transactionRepository.save(reversal);
        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.walletEntry(reversal, wallet, reversalAmount),
                LedgerEntry.externalEntry(reversal, EXTERNAL_ACCOUNT, reversalAmount.negate(), wallet.getCurrency())));
        original.markReversed();

        return TransactionResponse.from(reversal, wallet);
    }

    private TransactionResponse toResponse(FinancialTransaction transaction) {
        LedgerEntry walletEntry = ledgerEntryRepository.findFirstByTransactionIdAndAccountKind(transaction.getId(), AccountKind.WALLET)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSACTION_LEDGER_NOT_FOUND", "Transaction ledger entry was not found"));
        return TransactionResponse.from(transaction, walletEntry.getWallet());
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private Wallet requireOwnedWalletForUpdate(UUID userId, UUID walletId) {
        return walletRepository.findOwnedForUpdate(walletId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet was not found"));
    }

    private BigDecimal validateAndNormalizeAmount(BigDecimal requestedAmount, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT));
        int allowedScale = Math.max(currency.getDefaultFractionDigits(), 0);
        if (requestedAmount.scale() > allowedScale) {
            throw new IllegalArgumentException("Amount has more decimal places than the wallet currency supports");
        }
        return requestedAmount.setScale(allowedScale);
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must contain at most 100 characters");
        }
        return normalized;
    }

    private String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }

    private CategoryType categoryTypeFor(TransactionType transactionType) {
        return transactionType == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
    }
}
