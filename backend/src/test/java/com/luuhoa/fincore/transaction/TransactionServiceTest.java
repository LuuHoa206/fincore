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
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletRepository;
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
    private WalletRepository walletRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private CategoryService categoryService;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, ledgerEntryRepository, walletRepository, userRepository, categoryService);
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
                Instant.parse("2026-08-26T09:00:00Z"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findOwnedForUpdate(walletId, userId)).thenReturn(Optional.of(wallet));
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
                Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findOwnedForUpdate(walletId, userId)).thenReturn(Optional.of(wallet));
        when(categoryService.requireAvailableForTransaction(userId, categoryId, CategoryType.EXPENSE)).thenReturn(category);

        assertThatThrownBy(() -> service.create(userId, request, "expense-2026-08-26"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("enough available balance");

        assertThat(wallet.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(any());
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
        when(ledgerEntryRepository.findFirstByTransactionIdAndAccountKind(transaction.getId(), AccountKind.WALLET))
                .thenReturn(Optional.of(entry));

        TransactionPageResponse result = service.search(userId, TransactionType.EXPENSE, " lunch ", null, null, 1, 10);

        assertThat(result.content()).singleElement().extracting(TransactionResponse::description).isEqualTo("Lunch");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void rejectsAnUnsafePageSizeBeforeQueryingTransactions() {
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), null, null, null, null, 0, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 50");

        verify(transactionRepository, never()).searchByUser(any(), any(), any(), any(), any(), any());
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
