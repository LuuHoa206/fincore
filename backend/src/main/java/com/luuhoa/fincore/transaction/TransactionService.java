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
import com.luuhoa.fincore.allocationrule.AllocationRuleService;
import com.luuhoa.fincore.allocationrule.IncomeAllocationPlan;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletService;
import com.luuhoa.fincore.wallet.WalletTransferPair;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class TransactionService {

    private static final String EXTERNAL_ACCOUNT = "External source";

    private final FinancialTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletService walletService;
    private final UserAccountRepository userRepository;
    private final CategoryService categoryService;
    private final AllocationRuleService allocationRuleService;
    private final List<TransactionReversalGuard> transactionReversalGuards;

    public TransactionService(
            FinancialTransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            WalletService walletService,
            UserAccountRepository userRepository,
            CategoryService categoryService,
            AllocationRuleService allocationRuleService,
            List<TransactionReversalGuard> transactionReversalGuards) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
        this.allocationRuleService = allocationRuleService;
        this.transactionReversalGuards = List.copyOf(transactionReversalGuards);
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
        TransactionResponse existing = findExistingTransaction(userId, normalizedKey);
        if (existing != null) {
            return existing;
        }

        if (request.transactionType() != TransactionType.INCOME && request.transactionType() != TransactionType.EXPENSE) {
            throw new IllegalArgumentException("Only INCOME and EXPENSE are supported when recording a transaction");
        }

        UserAccount user = lockUserForFinancialWrite(userId);
        existing = findExistingTransaction(userId, normalizedKey);
        if (existing != null) {
            return existing;
        }
        boolean applyAllocationRule = request.transactionType() == TransactionType.INCOME
                && Boolean.TRUE.equals(request.applyAllocationRule());
        IncomeAllocationPlan allocationPlan = applyAllocationRule
                ? allocationRuleService.prepareIncomeAllocation(userId, request.walletId(), request.amount())
                : null;
        Wallet wallet = allocationPlan == null
                ? walletService.requireOwnedWalletForTransaction(userId, request.walletId())
                : allocationPlan.wallet();
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
        if (allocationPlan != null) {
            allocationRuleService.applyIncomeAllocation(allocationPlan, transaction);
        }

        return TransactionResponse.from(transaction, wallet);
    }

    @Transactional
    public TransactionResponse createTransfer(UUID userId, CreateWalletTransferRequest request, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        TransactionResponse existing = findExistingTransaction(userId, normalizedKey);
        if (existing != null) {
            return existing;
        }

        UserAccount user = lockUserForFinancialWrite(userId);
        existing = findExistingTransaction(userId, normalizedKey);
        if (existing != null) {
            return existing;
        }
        WalletTransferPair wallets = walletService.lockOwnedWalletsForTransfer(
                userId,
                request.sourceWalletId(),
                request.destinationWalletId());
        BigDecimal amount = validateAndNormalizeAmount(request.amount(), wallets.source().getCurrency());
        wallets.source().applyBalance(amount.negate());
        wallets.destination().applyBalance(amount);

        FinancialTransaction transfer = new FinancialTransaction(
                user,
                null,
                TransactionType.TRANSFER,
                amount,
                wallets.source().getCurrency(),
                request.description().trim(),
                normalizeNotes(request.notes()),
                request.occurredAt(),
                normalizedKey);
        transactionRepository.save(transfer);
        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.walletEntry(transfer, wallets.source(), amount.negate()),
                LedgerEntry.walletEntry(transfer, wallets.destination(), amount)));

        return TransactionResponse.from(transfer, wallets.source(), wallets.destination());
    }

    @Transactional
    public TransactionResponse reverse(UUID userId, UUID transactionId) {
        FinancialTransaction original = transactionRepository.findOwnedForUpdate(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSACTION_NOT_FOUND", "Transaction was not found"));
        transactionReversalGuards.forEach(guard -> guard.assertCanReverse(userId, transactionId));
        if (original.getStatus() != TransactionStatus.POSTED || original.getTransactionType() == TransactionType.REVERSAL
                || transactionRepository.existsByReversedTransactionId(transactionId)) {
            throw new ConflictException("TRANSACTION_CANNOT_BE_REVERSED", "This transaction has already been reversed or cannot be reversed");
        }

        List<LedgerEntry> walletEntries = ledgerEntryRepository.findAllByTransactionIdAndAccountKind(transactionId, AccountKind.WALLET);
        if (walletEntries.isEmpty()) {
            throw new ResourceNotFoundException("TRANSACTION_LEDGER_NOT_FOUND", "Transaction ledger entry was not found");
        }
        FinancialTransaction reversal = FinancialTransaction.reversalOf(original, requireUser(userId));
        TransactionResponse response;
        if (walletEntries.size() == 1) {
            LedgerEntry originalWalletEntry = walletEntries.getFirst();
            Wallet wallet = walletService.requireOwnedWalletForTransaction(userId, originalWalletEntry.getWallet().getId());
            BigDecimal reversalAmount = originalWalletEntry.getSignedAmount().negate();
            wallet.applyBalance(reversalAmount);
            transactionRepository.save(reversal);
            ledgerEntryRepository.saveAll(List.of(
                    LedgerEntry.walletEntry(reversal, wallet, reversalAmount),
                    LedgerEntry.externalEntry(reversal, EXTERNAL_ACCOUNT, reversalAmount.negate(), wallet.getCurrency())));
            response = TransactionResponse.from(reversal, wallet);
        } else if (original.getTransactionType() == TransactionType.TRANSFER && walletEntries.size() == 2) {
            WalletTransferPair lockedWallets = walletService.lockOwnedWalletsForTransfer(
                    userId,
                    walletEntries.get(0).getWallet().getId(),
                    walletEntries.get(1).getWallet().getId());
            List<LedgerEntry> reversalEntries = walletEntries.stream()
                    .map(entry -> {
                        Wallet wallet = entry.getWallet().getId().equals(lockedWallets.source().getId())
                                ? lockedWallets.source()
                                : lockedWallets.destination();
                        BigDecimal reversalAmount = entry.getSignedAmount().negate();
                        wallet.applyBalance(reversalAmount);
                        return LedgerEntry.walletEntry(reversal, wallet, reversalAmount);
                    })
                    .toList();
            transactionRepository.save(reversal);
            ledgerEntryRepository.saveAll(reversalEntries);
            LedgerEntry sourceEntry = reversalEntries.stream()
                    .filter(entry -> entry.getSignedAmount().signum() < 0)
                    .findFirst()
                    .orElseThrow(() -> new ConflictException("TRANSACTION_CANNOT_BE_REVERSED", "Transfer reversal is inconsistent"));
            LedgerEntry destinationEntry = reversalEntries.stream()
                    .filter(entry -> entry.getSignedAmount().signum() > 0)
                    .findFirst()
                    .orElseThrow(() -> new ConflictException("TRANSACTION_CANNOT_BE_REVERSED", "Transfer reversal is inconsistent"));
            response = TransactionResponse.from(reversal, sourceEntry.getWallet(), destinationEntry.getWallet());
        } else {
            throw new ConflictException("TRANSACTION_CANNOT_BE_REVERSED", "Transaction ledger entries are inconsistent");
        }
        original.markReversed();

        return response;
    }

    private TransactionResponse toResponse(FinancialTransaction transaction) {
        List<LedgerEntry> walletEntries = ledgerEntryRepository.findAllByTransactionIdAndAccountKind(transaction.getId(), AccountKind.WALLET);
        if (walletEntries.isEmpty()) {
            throw new ResourceNotFoundException("TRANSACTION_LEDGER_NOT_FOUND", "Transaction ledger entry was not found");
        }
        if (walletEntries.size() == 1) {
            return TransactionResponse.from(transaction, walletEntries.getFirst().getWallet());
        }
        if (walletEntries.size() == 2 && (transaction.getTransactionType() == TransactionType.TRANSFER
                || transaction.getTransactionType() == TransactionType.REVERSAL)) {
            LedgerEntry sourceEntry = walletEntries.stream()
                    .filter(entry -> entry.getSignedAmount().signum() < 0)
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("TRANSACTION_LEDGER_NOT_FOUND", "Transfer source entry was not found"));
            LedgerEntry destinationEntry = walletEntries.stream()
                    .filter(entry -> entry.getSignedAmount().signum() > 0)
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("TRANSACTION_LEDGER_NOT_FOUND", "Transfer destination entry was not found"));
            return TransactionResponse.from(transaction, sourceEntry.getWallet(), destinationEntry.getWallet());
        }
        throw new ResourceNotFoundException("TRANSACTION_LEDGER_NOT_FOUND", "Transaction ledger entries are inconsistent");
    }

    private UserAccount requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    /**
     * A user-level row lock gives idempotency a single serialization point before
     * a money-changing request locks individual wallets. This prevents two
     * concurrent requests with the same key from creating two ledger postings.
     */
    private UserAccount lockUserForFinancialWrite(UUID userId) {
        return userRepository.findByIdForFinancialWrite(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    private TransactionResponse findExistingTransaction(UUID userId, String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        return transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(this::toResponse)
                .orElse(null);
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
