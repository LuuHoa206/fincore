package com.luuhoa.fincore.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.allocationrule.AllocationRuleService;
import com.luuhoa.fincore.allocationrule.IncomeAllocationPlan;
import com.luuhoa.fincore.audit.AuditLogService;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletService;
import com.luuhoa.fincore.wallet.WalletTransferPair;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private FinancialTransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private AllocationRuleService allocationRuleService;

    @Mock
    private AuditLogService auditLogService;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, ledgerEntryRepository, walletService, userRepository, categoryService, allocationRuleService, auditLogService, List.of());
    }

    @SuppressWarnings("unchecked")
    @Test
    void recordsIncomeAsBalancedLedgerEntriesAndUpdatesWalletOnce() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UserAccount user = user();
        Category category = new Category(user, "Salary", CategoryType.INCOME, "banknote", "#0F8F72");
        Wallet wallet = new Wallet(user, "Salary account", WalletType.BANK, "VND", false);
        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId,
                categoryId,
                TransactionType.INCOME,
                new BigDecimal("15000000"),
                "August salary",
                null,
                Instant.parse("2026-08-26T09:00:00Z"),
                false);

        when(userRepository.findByIdForFinancialWrite(userId)).thenReturn(Optional.of(user));
        when(walletService.requireOwnedWalletForTransaction(userId, walletId)).thenReturn(wallet);
        when(categoryService.requireAvailableForTransaction(userId, categoryId, CategoryType.INCOME)).thenReturn(category);

        service.create(userId, request, "income-2026-08-26");

        assertThat(wallet.getCurrentBalance()).isEqualByComparingTo("15000000");
        ArgumentCaptor<List<LedgerEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(entries.capture());
        assertThat(entries.getValue()).hasSize(2);
        assertThat(entries.getValue().stream().map(LedgerEntry::getSignedAmount))
                .containsExactlyInAnyOrder(new BigDecimal("15000000"), new BigDecimal("-15000000"));
        verify(transactionRepository).save(any(FinancialTransaction.class));
    }

    @Test
    void rejectsExpenseWhenWalletDoesNotAllowNegativeBalance() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UserAccount user = user();
        Category category = new Category(user, "Food", CategoryType.EXPENSE, "utensils", "#EA580C");
        Wallet wallet = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId,
                categoryId,
                TransactionType.EXPENSE,
                new BigDecimal("100000"),
                "Lunch",
                null,
                Instant.now(),
                false);

        when(userRepository.findByIdForFinancialWrite(userId)).thenReturn(Optional.of(user));
        when(walletService.requireOwnedWalletForTransaction(userId, walletId)).thenReturn(wallet);
        when(categoryService.requireAvailableForTransaction(userId, categoryId, CategoryType.EXPENSE)).thenReturn(category);

        assertThatThrownBy(() -> service.create(userId, request, "expense-2026-08-26"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("enough available balance");

        assertThat(wallet.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void appliesThePreparedAllocationRuleOnlyAfterRecordingIncome() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UserAccount user = user();
        Wallet wallet = new Wallet(user, "Salary account", WalletType.BANK, "VND", false);
        Category category = new Category(user, "Salary", CategoryType.INCOME, "banknote", "#0F8F72");
        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId, categoryId, TransactionType.INCOME, new BigDecimal("1000000"), "August salary", null,
                Instant.parse("2026-08-26T09:00:00Z"), true);
        IncomeAllocationPlan plan = new IncomeAllocationPlan(wallet, List.of(), List.of(), BigDecimal.ZERO);

        when(userRepository.findByIdForFinancialWrite(userId)).thenReturn(Optional.of(user));
        when(allocationRuleService.prepareIncomeAllocation(userId, walletId, request.amount())).thenReturn(plan);
        when(categoryService.requireAvailableForTransaction(userId, categoryId, CategoryType.INCOME)).thenReturn(category);

        service.create(userId, request, "allocated-income-2026-08-26");

        assertThat(wallet.getCurrentBalance()).isEqualByComparingTo("1000000");
        verify(transactionRepository).save(any(FinancialTransaction.class));
        verify(ledgerEntryRepository).saveAll(any());
        verify(allocationRuleService).applyIncomeAllocation(org.mockito.ArgumentMatchers.eq(plan), org.mockito.ArgumentMatchers.any(FinancialTransaction.class));
        verify(walletService, never()).requireOwnedWalletForTransaction(userId, walletId);
    }

    @Test
    void returnsTheExistingTransactionWhenTheSameKeyAppearsAfterTheFinancialWriteLock() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        String idempotencyKey = "income-duplicate-after-lock";
        UserAccount user = user();
        Wallet wallet = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        Category category = new Category(user, "Salary", CategoryType.INCOME, "banknote", "#0F8F72");
        FinancialTransaction existing = new FinancialTransaction(
                user, category, TransactionType.INCOME, new BigDecimal("500000"), "VND", "Salary", null, Instant.now(), idempotencyKey);
        setId(existing, transactionId);
        LedgerEntry existingEntry = LedgerEntry.walletEntry(existing, wallet, new BigDecimal("500000"));
        CreateTransactionRequest request = new CreateTransactionRequest(
                walletId, categoryId, TransactionType.INCOME, new BigDecimal("500000"), "Salary", null, Instant.now(), false);

        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(userRepository.findByIdForFinancialWrite(userId)).thenReturn(Optional.of(user));
        when(ledgerEntryRepository.findAllByTransactionIdAndAccountKind(transactionId, AccountKind.WALLET))
                .thenReturn(List.of(existingEntry));

        TransactionResponse response = service.create(userId, request, idempotencyKey);

        assertThat(response.id()).isEqualTo(transactionId);
        verify(walletService, never()).requireOwnedWalletForTransaction(userId, walletId);
        verify(categoryService, never()).requireAvailableForTransaction(userId, categoryId, CategoryType.INCOME);
        verify(transactionRepository, never()).save(any(FinancialTransaction.class));
        verify(ledgerEntryRepository, never()).saveAll(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void searchesTransactionsOnTheServerAndReturnsPageMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount user = user();
        Wallet wallet = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        FinancialTransaction transaction = new FinancialTransaction(
                user, null, TransactionType.EXPENSE, new BigDecimal("50000"), "VND", "Lunch", null, Instant.now(), null);
        setId(transaction, UUID.randomUUID());
        LedgerEntry entry = LedgerEntry.walletEntry(transaction, wallet, new BigDecimal("-50000"));
        PageRequest pageRequest = PageRequest.of(1, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "occurredAt"));

        when(transactionRepository.searchByUser(userId, TransactionType.EXPENSE, "lunch", null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(transaction), pageRequest, 11));
        when(ledgerEntryRepository.findAllByTransactionIdAndAccountKind(transaction.getId(), AccountKind.WALLET))
                .thenReturn(List.of(entry));

        TransactionPageResponse result = service.search(userId, TransactionType.EXPENSE, " lunch ", null, null, 1, 10);

        assertThat(result.content()).singleElement().extracting(TransactionResponse::description).isEqualTo("Lunch");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void recordsAnInternalTransferAsTwoBalancedWalletEntries() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        UserAccount user = user();
        Wallet source = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        Wallet destination = new Wallet(user, "Bank", WalletType.BANK, "VND", false);
        setId(source, sourceWalletId);
        setId(destination, destinationWalletId);
        source.applyBalance(new BigDecimal("1000000"));
        CreateWalletTransferRequest request = new CreateWalletTransferRequest(
                sourceWalletId,
                destinationWalletId,
                new BigDecimal("250000"),
                "Nộp tiền vào ngân hàng",
                "Cuối ngày",
                Instant.parse("2026-08-27T10:30:00Z"));

        when(userRepository.findByIdForFinancialWrite(userId)).thenReturn(Optional.of(user));
        when(walletService.lockOwnedWalletsForTransfer(userId, sourceWalletId, destinationWalletId))
                .thenReturn(new WalletTransferPair(source, destination));

        TransactionResponse response = service.createTransfer(userId, request, "transfer-2026-08-27-1");

        assertThat(source.getCurrentBalance()).isEqualByComparingTo("750000");
        assertThat(destination.getCurrentBalance()).isEqualByComparingTo("250000");
        assertThat(response.transactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(response.walletName()).isEqualTo("Cash");
        assertThat(response.counterpartyWalletName()).isEqualTo("Bank");
        ArgumentCaptor<List<LedgerEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(entries.capture());
        assertThat(entries.getValue()).extracting(LedgerEntry::getSignedAmount)
                .containsExactlyInAnyOrder(new BigDecimal("-250000"), new BigDecimal("250000"));
        verify(transactionRepository).save(any(FinancialTransaction.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void reversesBothWalletSidesOfAnInternalTransfer() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UserAccount user = user();
        Wallet source = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        Wallet destination = new Wallet(user, "Bank", WalletType.BANK, "VND", false);
        setId(source, sourceWalletId);
        setId(destination, destinationWalletId);
        source.applyBalance(new BigDecimal("750000"));
        destination.applyBalance(new BigDecimal("250000"));
        FinancialTransaction original = new FinancialTransaction(
                user, null, TransactionType.TRANSFER, new BigDecimal("250000"), "VND", "Nộp tiền vào ngân hàng", null, Instant.now(), null);
        setId(original, transactionId);
        LedgerEntry sourceEntry = LedgerEntry.walletEntry(original, source, new BigDecimal("-250000"));
        LedgerEntry destinationEntry = LedgerEntry.walletEntry(original, destination, new BigDecimal("250000"));

        when(transactionRepository.findOwnedForUpdate(transactionId, userId)).thenReturn(Optional.of(original));
        when(transactionRepository.existsByReversedTransactionId(transactionId)).thenReturn(false);
        when(ledgerEntryRepository.findAllByTransactionIdAndAccountKind(transactionId, AccountKind.WALLET))
                .thenReturn(List.of(sourceEntry, destinationEntry));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletService.lockOwnedWalletsForTransfer(userId, sourceWalletId, destinationWalletId))
                .thenReturn(new WalletTransferPair(source, destination));

        TransactionResponse response = service.reverse(userId, transactionId);

        assertThat(source.getCurrentBalance()).isEqualByComparingTo("1000000");
        assertThat(destination.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(original.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(response.transactionType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(response.walletName()).isEqualTo("Bank");
        assertThat(response.counterpartyWalletName()).isEqualTo("Cash");
        ArgumentCaptor<List<LedgerEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(entries.capture());
        assertThat(entries.getValue()).extracting(LedgerEntry::getSignedAmount)
                .containsExactlyInAnyOrder(new BigDecimal("250000"), new BigDecimal("-250000"));
    }

    @Test
    void rejectsAnUnsafePageSizeBeforeQueryingTransactions() {
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), null, null, null, null, 0, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 50");

        verify(transactionRepository, never()).searchByUser(any(), any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void recordsARecomputedStatementDifferenceAsASeparateBalancedAdjustment() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UserAccount user = user();
        Wallet wallet = new Wallet(user, "Main", WalletType.BANK, "VND", false);
        setId(wallet, walletId);
        wallet.applyBalance(new BigDecimal("900000"));
        LedgerEntryRepository.WalletLedgerBalance summary = org.mockito.Mockito.mock(LedgerEntryRepository.WalletLedgerBalance.class);
        when(summary.getBalance()).thenReturn(new BigDecimal("900000"));
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, "statement-adjustment-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByIdForFinancialWrite(userId)).thenReturn(Optional.of(user));
        when(walletService.requireOwnedWalletForTransaction(userId, walletId)).thenReturn(wallet);
        when(ledgerEntryRepository.summarizeWalletBalanceUntil(userId, walletId, Instant.parse("2026-08-26T17:00:00Z")))
                .thenReturn(summary);

        TransactionResponse response = service.createReconciliationAdjustment(userId,
                new ReconciliationAdjustmentCommand(walletId, java.time.LocalDate.of(2026, 8, 26),
                        new BigDecimal("1000000"), Instant.parse("2026-08-26T17:00:00Z"), "Đã kiểm tra sao kê"),
                "statement-adjustment-1");

        assertThat(response.transactionType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(response.walletChange()).isEqualByComparingTo("100000");
        assertThat(wallet.getCurrentBalance()).isEqualByComparingTo("1000000");
        ArgumentCaptor<List<LedgerEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(entries.capture());
        assertThat(entries.getValue()).extracting(LedgerEntry::getSignedAmount)
                .containsExactlyInAnyOrder(new BigDecimal("100000"), new BigDecimal("-100000"));
        verify(auditLogService).record(org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq("WALLET_RECONCILIATION_ADJUSTED"),
                org.mockito.ArgumentMatchers.eq("TRANSACTION"), any(), any());
    }

    @Test
    void rejectsAnAdjustmentWhenTheStatementAlreadyMatchesTheLedger() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UserAccount user = user();
        Wallet wallet = new Wallet(user, "Main", WalletType.BANK, "VND", false);
        setId(wallet, walletId);
        LedgerEntryRepository.WalletLedgerBalance summary = org.mockito.Mockito.mock(LedgerEntryRepository.WalletLedgerBalance.class);
        when(summary.getBalance()).thenReturn(new BigDecimal("1000000"));
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, "matched-adjustment"))
                .thenReturn(Optional.empty());
        when(userRepository.findByIdForFinancialWrite(userId)).thenReturn(Optional.of(user));
        when(walletService.requireOwnedWalletForTransaction(userId, walletId)).thenReturn(wallet);
        when(ledgerEntryRepository.summarizeWalletBalanceUntil(userId, walletId, Instant.parse("2026-08-26T17:00:00Z")))
                .thenReturn(summary);

        assertThatThrownBy(() -> service.createReconciliationAdjustment(userId,
                new ReconciliationAdjustmentCommand(walletId, java.time.LocalDate.of(2026, 8, 26),
                        new BigDecimal("1000000"), Instant.parse("2026-08-26T17:00:00Z"), "Đã kiểm tra sao kê"),
                "matched-adjustment"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already matches");

        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void returnsTheExistingAdjustmentWhenTheConfirmationIsRetried() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UserAccount user = user();
        Wallet wallet = new Wallet(user, "Main", WalletType.BANK, "VND", false);
        setId(wallet, walletId);
        FinancialTransaction existing = new FinancialTransaction(
                user, null, TransactionType.ADJUSTMENT, new BigDecimal("100000"), "VND",
                "Điều chỉnh theo đối soát sao kê", "Đã kiểm tra sao kê",
                Instant.parse("2026-08-26T16:59:59.999999999Z"), "retry-adjustment");
        setId(existing, transactionId);
        LedgerEntry entry = LedgerEntry.walletEntry(existing, wallet, new BigDecimal("100000"));
        when(transactionRepository.findByUserIdAndIdempotencyKey(userId, "retry-adjustment"))
                .thenReturn(Optional.of(existing));
        when(ledgerEntryRepository.findAllByTransactionIdAndAccountKind(transactionId, AccountKind.WALLET))
                .thenReturn(List.of(entry));

        TransactionResponse response = service.createReconciliationAdjustment(userId,
                new ReconciliationAdjustmentCommand(walletId, java.time.LocalDate.of(2026, 8, 26),
                        new BigDecimal("1000000"), Instant.parse("2026-08-26T17:00:00Z"), "Đã kiểm tra sao kê"),
                "retry-adjustment");

        assertThat(response.id()).isEqualTo(transactionId);
        verify(userRepository, never()).findByIdForFinancialWrite(any());
        verify(walletService, never()).requireOwnedWalletForTransaction(any(), any());
        verify(ledgerEntryRepository, never()).saveAll(any());
        verify(transactionRepository, never()).save(any());
    }

    private UserAccount user() {
        return new UserAccount(
                "owner@example.com",
                "hash",
                "Owner",
                "VND",
                "Asia/Ho_Chi_Minh");
    }

    private void setId(Object target, UUID id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
