package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a spreadsheet-friendly snapshot of a user's transaction history.
 * Export is read-only and never changes a wallet, ledger entry, or transaction.
 */
@Service
public class TransactionCsvExportService {

    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final String UTF8_BOM = "\uFEFF";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final FinancialTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserAccountRepository userRepository;

    public TransactionCsvExportService(
            FinancialTransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            UserAccountRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public byte[] export(
            UUID userId,
            TransactionType transactionType,
            String query,
            Instant fromTime,
            Instant toTime) {
        if (fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw new IllegalArgumentException("from must be before to");
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        String searchTerm = query == null || query.isBlank() ? null : query.trim();
        var result = transactionRepository.searchByUser(
                userId,
                transactionType,
                searchTerm,
                fromTime,
                toTime,
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        if (result.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new ConflictException(
                    "TRANSACTION_EXPORT_LIMIT_EXCEEDED",
                    "Refine the filters before exporting more than 10000 transactions");
        }

        List<FinancialTransaction> transactions = result.getContent();
        Map<UUID, List<LedgerEntry>> walletEntriesByTransaction = transactions.isEmpty()
                ? Map.of()
                : ledgerEntryRepository.findAllByTransactionIdInAndAccountKindWithWallet(
                                transactions.stream().map(FinancialTransaction::getId).toList(),
                                AccountKind.WALLET)
                        .stream()
                        .collect(Collectors.groupingBy(entry -> entry.getTransaction().getId()));
        ZoneId zoneId = ZoneId.of(user.getTimeZone());
        return buildCsv(transactions, walletEntriesByTransaction, zoneId).getBytes(StandardCharsets.UTF_8);
    }

    private String buildCsv(
            List<FinancialTransaction> transactions,
            Map<UUID, List<LedgerEntry>> walletEntriesByTransaction,
            ZoneId zoneId) {
        StringBuilder csv = new StringBuilder(UTF8_BOM)
                .append(row(
                        "Mã giao dịch",
                        "Thời gian",
                        "Loại",
                        "Trạng thái",
                        "Nội dung",
                        "Danh mục",
                        "Ví nguồn",
                        "Ví đích",
                        "Số tiền",
                        "Tiền tệ",
                        "Ghi chú",
                        "Hoàn tác giao dịch"));
        for (FinancialTransaction transaction : transactions) {
            WalletColumns wallets = walletsFor(transaction, walletEntriesByTransaction.getOrDefault(transaction.getId(), List.of()));
            csv.append(row(
                    transaction.getId().toString(),
                    DATE_TIME_FORMATTER.format(transaction.getOccurredAt().atZone(zoneId)),
                    transactionTypeLabel(transaction.getTransactionType()),
                    transactionStatusLabel(transaction.getStatus()),
                    transaction.getDescription(),
                    transaction.getCategory() == null ? "" : transaction.getCategory().getName(),
                    wallets.sourceName(),
                    wallets.destinationName(),
                    transaction.getAmount().stripTrailingZeros().toPlainString(),
                    transaction.getCurrency(),
                    nullToEmpty(transaction.getNotes()),
                    transaction.getReversedTransaction() == null ? "" : transaction.getReversedTransaction().getId().toString()));
        }
        return csv.toString();
    }

    private WalletColumns walletsFor(FinancialTransaction transaction, List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return new WalletColumns("", "");
        }
        boolean isTransfer = transaction.getTransactionType() == TransactionType.TRANSFER
                || transaction.getTransactionType() == TransactionType.REVERSAL && entries.size() == 2;
        if (!isTransfer) {
            return new WalletColumns(entries.getFirst().getWallet().getName(), "");
        }
        String source = entries.stream()
                .filter(entry -> entry.getSignedAmount().signum() < 0)
                .map(entry -> entry.getWallet().getName())
                .findFirst()
                .orElse("");
        String destination = entries.stream()
                .filter(entry -> entry.getSignedAmount().signum() > 0)
                .map(entry -> entry.getWallet().getName())
                .findFirst()
                .orElse("");
        return new WalletColumns(source, destination);
    }

    private String row(String... columns) {
        return java.util.Arrays.stream(columns)
                .map(this::escape)
                .collect(Collectors.joining(",", "", "\r\n"));
    }

    private String escape(String value) {
        return '"' + nullToEmpty(value).replace("\"", "\"\"") + '"';
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String transactionTypeLabel(TransactionType type) {
        return switch (type) {
            case INCOME -> "Thu nhập";
            case EXPENSE -> "Chi tiêu";
            case TRANSFER -> "Chuyển tiền";
            case JAR_TRANSFER -> "Phân bổ hũ";
            case REFUND -> "Hoàn tiền";
            case ADJUSTMENT -> "Điều chỉnh";
            case REVERSAL -> "Hoàn tác";
        };
    }

    private String transactionStatusLabel(TransactionStatus status) {
        return switch (status) {
            case PENDING -> "Đang chờ";
            case POSTED -> "Đã ghi nhận";
            case REVERSED -> "Đã hoàn tác";
            case FAILED -> "Không thành công";
        };
    }

    private record WalletColumns(String sourceName, String destinationName) {
    }
}
