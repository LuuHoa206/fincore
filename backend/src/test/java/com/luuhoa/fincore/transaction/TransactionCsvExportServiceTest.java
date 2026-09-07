package com.luuhoa.fincore.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.category.Category;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.wallet.Wallet;
import com.luuhoa.fincore.wallet.WalletType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TransactionCsvExportServiceTest {

    @Mock private FinancialTransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private UserAccountRepository userRepository;

    @Test
    void exportsOwnedTransactionsAsExcelFriendlyVietnameseCsv() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount("user@example.com", "hash", "User", "VND", "Asia/Ho_Chi_Minh");
        Category category = new Category(user, "Ăn uống", CategoryType.EXPENSE, "utensils", "#EA580C");
        FinancialTransaction transaction = new FinancialTransaction(
                user,
                category,
                TransactionType.EXPENSE,
                new BigDecimal("35000"),
                "VND",
                "Cà phê \"sáng\"",
                "Mang đi",
                Instant.parse("2026-08-27T01:00:00Z"),
                null);
        UUID transactionId = UUID.randomUUID();
        setId(transaction, transactionId);
        Wallet wallet = new Wallet(user, "Ví tiền mặt", WalletType.CASH, "VND", false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(transactionRepository.searchByUser(
                eq(userId),
                eq(true),
                eq(TransactionType.EXPENSE),
                eq("cà phê"),
                eq(false),
                eq(Instant.EPOCH),
                eq(false),
                eq(Instant.EPOCH),
                any()))
                .thenReturn(new PageImpl<>(List.of(transaction), PageRequest.of(0, 10_000), 1));
        when(ledgerEntryRepository.findAllByTransactionIdInAndAccountKindWithWallet(List.of(transactionId), AccountKind.WALLET))
                .thenReturn(List.of(LedgerEntry.walletEntry(transaction, wallet, new BigDecimal("-35000"))));

        byte[] result = service().export(userId, TransactionType.EXPENSE, "  cà phê  ", null, null);
        String csv = new String(result, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFF\"Mã giao dịch\"");
        assertThat(csv).contains("\"2026-08-27 08:00:00\"");
        assertThat(csv).contains("\"Chi tiêu\"");
        assertThat(csv).contains("\"Cà phê \"\"sáng\"\"\"");
        assertThat(csv).contains("\"Ăn uống\"");
        assertThat(csv).contains("\"Ví tiền mặt\"");
        assertThat(csv).contains("\"35000\"");
        verify(ledgerEntryRepository).findAllByTransactionIdInAndAccountKindWithWallet(List.of(transactionId), AccountKind.WALLET);
    }

    @Test
    void refusesToSilentlyTruncateAFileAboveTheExportLimit() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount("user@example.com", "hash", "User", "VND", "Asia/Ho_Chi_Minh");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(transactionRepository.searchByUser(
                eq(userId),
                eq(false),
                eq(TransactionType.INCOME),
                eq(""),
                eq(false),
                eq(Instant.EPOCH),
                eq(false),
                eq(Instant.EPOCH),
                any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10_000), 10_001));

        assertThatThrownBy(() -> service().export(userId, null, null, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Refine the filters before exporting more than 10000 transactions");
        verify(ledgerEntryRepository, never()).findAllByTransactionIdInAndAccountKindWithWallet(any(), any());
    }

    private TransactionCsvExportService service() {
        return new TransactionCsvExportService(transactionRepository, ledgerEntryRepository, userRepository);
    }

    private void setId(Object target, UUID id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
