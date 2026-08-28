package com.luuhoa.fincore.statementimport;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.category.CategoryService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.UserResponse;
import com.luuhoa.fincore.transaction.CreateTransactionRequest;
import com.luuhoa.fincore.transaction.TransactionCreateResult;
import com.luuhoa.fincore.transaction.TransactionResponse;
import com.luuhoa.fincore.transaction.TransactionService;
import com.luuhoa.fincore.transaction.TransactionType;
import com.luuhoa.fincore.wallet.WalletResponse;
import com.luuhoa.fincore.wallet.WalletService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a deliberately small, documented CSV shape. Raw bank statement text
 * is never persisted: it is parsed in memory, previewed, then each accepted
 * row is posted through {@link TransactionService} with a stable idempotency
 * key. This keeps wallet balances and the accounting ledger authoritative.
 */
@Service
public class StatementImportService {

    private static final int MAX_ROWS = 200;
    private static final DateTimeFormatter ISO_LOCAL_MINUTES = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private static final DateTimeFormatter VIETNAMESE_LOCAL_MINUTES = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");
    private static final DateTimeFormatter VIETNAMESE_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final AuthService authService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;

    public StatementImportService(
            AuthService authService,
            WalletService walletService,
            CategoryService categoryService,
            TransactionService transactionService) {
        this.authService = authService;
        this.walletService = walletService;
        this.categoryService = categoryService;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public StatementImportPreview preview(UUID userId, StatementImportRequest request) {
        UserResponse user = authService.currentUser(userId);
        WalletResponse wallet = walletService.get(userId, request.walletId());
        ZoneId zoneId = ZoneId.of(user.timeZone());
        List<ParsedRow> parsedRows = parse(request.csvText(), wallet.currency(), zoneId);
        validateSelectedCategories(userId, request, parsedRows);

        List<StatementImportRow> rows = parsedRows.stream()
                .map(row -> toPreviewRow(userId, request.walletId(), row))
                .toList();
        int readyCount = count(rows, StatementImportRowStatus.READY);
        int duplicateCount = count(rows, StatementImportRowStatus.DUPLICATE);
        int invalidCount = count(rows, StatementImportRowStatus.INVALID);
        return new StatementImportPreview(
                wallet.id(), wallet.currency(), rows.size(), readyCount, duplicateCount, invalidCount, rows);
    }

    /**
     * The import is all-or-nothing for invalid input or a balance violation.
     * Rows already present from an earlier confirmation are harmlessly skipped.
     */
    @Transactional
    public StatementImportResult confirm(UUID userId, StatementImportRequest request) {
        StatementImportPreview preview = preview(userId, request);
        if (preview.invalidCount() > 0) {
            throw new IllegalArgumentException("Fix every invalid CSV row before confirming the import");
        }

        List<ParsedRow> parsedRows = parse(request.csvText(), preview.currency(),
                ZoneId.of(authService.currentUser(userId).timeZone()));
        List<TransactionResponse> imported = new ArrayList<>();
        int skippedDuplicateCount = 0;
        for (ParsedRow row : parsedRows) {
            String idempotencyKey = idempotencyKey(request.walletId(), row);
            if (transactionService.hasRecordedIdempotencyKey(userId, idempotencyKey)) {
                skippedDuplicateCount++;
                continue;
            }
            UUID categoryId = categoryIdFor(request, row.transactionType());
            TransactionCreateResult outcome = transactionService.createWithOutcome(userId,
                    new CreateTransactionRequest(
                            request.walletId(), categoryId, row.transactionType(), row.amount(), row.description(),
                            row.notes(), row.occurredAt(), false),
                    idempotencyKey);
            if (outcome.created()) {
                imported.add(outcome.transaction());
            } else {
                skippedDuplicateCount++;
            }
        }
        return new StatementImportResult(imported.size(), skippedDuplicateCount, List.copyOf(imported));
    }

    private void validateSelectedCategories(UUID userId, StatementImportRequest request, List<ParsedRow> rows) {
        boolean needsIncomeCategory = rows.stream().anyMatch(row -> row.isValid() && row.transactionType() == TransactionType.INCOME);
        boolean needsExpenseCategory = rows.stream().anyMatch(row -> row.isValid() && row.transactionType() == TransactionType.EXPENSE);
        if (needsIncomeCategory && request.incomeCategoryId() == null) {
            rows.replaceAll(row -> row.isValid() && row.transactionType() == TransactionType.INCOME
                    ? row.invalid("Choose an income category for imported income") : row);
        }
        if (needsExpenseCategory && request.expenseCategoryId() == null) {
            rows.replaceAll(row -> row.isValid() && row.transactionType() == TransactionType.EXPENSE
                    ? row.invalid("Choose an expense category for imported expenses") : row);
        }
        if (needsIncomeCategory && request.incomeCategoryId() != null) {
            categoryService.requireAvailableForTransaction(userId, request.incomeCategoryId(), CategoryType.INCOME);
        }
        if (needsExpenseCategory && request.expenseCategoryId() != null) {
            categoryService.requireAvailableForTransaction(userId, request.expenseCategoryId(), CategoryType.EXPENSE);
        }
    }

    private StatementImportRow toPreviewRow(UUID userId, UUID walletId, ParsedRow row) {
        if (!row.isValid()) {
            return row.toResponse(StatementImportRowStatus.INVALID, row.error());
        }
        boolean duplicate = transactionService.hasRecordedIdempotencyKey(userId, idempotencyKey(walletId, row));
        return row.toResponse(duplicate ? StatementImportRowStatus.DUPLICATE : StatementImportRowStatus.READY,
                duplicate ? "This statement line was imported before" : null);
    }

    private List<ParsedRow> parse(String csvText, String currencyCode, ZoneId zoneId) {
        List<List<String>> csvRows = CsvReader.parse(csvText);
        if (csvRows.size() < 2) {
            throw new IllegalArgumentException("CSV must contain a header and at least one transaction row");
        }
        Map<String, Integer> columns = headerColumns(csvRows.getFirst());
        List<ParsedRow> rows = new ArrayList<>();
        for (int index = 1; index < csvRows.size(); index++) {
            List<String> values = csvRows.get(index);
            if (values.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (rows.size() == MAX_ROWS) {
                throw new IllegalArgumentException("CSV supports at most " + MAX_ROWS + " transaction rows");
            }
            rows.add(parseRow(index + 1, values, columns, currencyCode, zoneId));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV must contain at least one transaction row");
        }
        return rows;
    }

    private Map<String, Integer> headerColumns(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            String name = header.get(index).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT)
                    .replace("_", "").replace("-", "").replace(" ", "");
            columns.putIfAbsent(name, index);
        }
        Map<String, Integer> result = new HashMap<>();
        result.put("date", firstColumn(columns, "date", "occurredat", "transactiondate"));
        result.put("type", firstColumn(columns, "type", "transactiontype"));
        result.put("amount", firstColumn(columns, "amount", "value"));
        result.put("description", firstColumn(columns, "description", "content", "memo"));
        result.put("notes", firstOptionalColumn(columns, "notes", "note"));
        return result;
    }

    private int firstColumn(Map<String, Integer> columns, String... names) {
        int index = firstOptionalColumn(columns, names);
        if (index < 0) {
            throw new IllegalArgumentException("CSV header must include date, type, amount and description");
        }
        return index;
    }

    private int firstOptionalColumn(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(name);
            if (index != null) {
                return index;
            }
        }
        return -1;
    }

    private ParsedRow parseRow(int rowNumber, List<String> values, Map<String, Integer> columns, String currencyCode, ZoneId zoneId) {
        try {
            Instant occurredAt = parseOccurredAt(cell(values, columns.get("date")), zoneId);
            TransactionType transactionType = parseType(cell(values, columns.get("type")));
            BigDecimal amount = normalizeAmount(cell(values, columns.get("amount")), currencyCode);
            String description = normalizeDescription(cell(values, columns.get("description")));
            String notes = normalizeNotes(columns.get("notes") < 0 ? null : cell(values, columns.get("notes")));
            return ParsedRow.valid(rowNumber, transactionType, amount, description, notes, occurredAt);
        } catch (IllegalArgumentException exception) {
            return ParsedRow.invalid(rowNumber, exception.getMessage());
        }
    }

    private String cell(List<String> values, int index) {
        return index >= 0 && index < values.size() ? values.get(index).trim() : "";
    }

    private Instant parseOccurredAt(String value, ZoneId zoneId) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Date is required");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Local bank statement dates intentionally use the user's configured time zone.
        }
        for (DateTimeFormatter formatter : List.of(ISO_LOCAL_MINUTES, VIETNAMESE_LOCAL_MINUTES)) {
            try {
                return LocalDateTime.parse(value, formatter).atZone(zoneId).toInstant();
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, VIETNAMESE_DATE)) {
            try {
                return LocalDate.parse(value, formatter).atStartOfDay(zoneId).toInstant();
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        throw new IllegalArgumentException("Date must use ISO date/time or dd/MM/yyyy");
    }

    private TransactionType parseType(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "INCOME", "THU" -> TransactionType.INCOME;
            case "EXPENSE", "CHI" -> TransactionType.EXPENSE;
            default -> throw new IllegalArgumentException("Type must be INCOME/THU or EXPENSE/CHI");
        };
    }

    private BigDecimal normalizeAmount(String value, String currencyCode) {
        String compact = value.replace("\u00A0", "").replaceAll("\\s+", "");
        if (compact.isBlank()) {
            throw new IllegalArgumentException("Amount is required");
        }
        int scale = Math.max(Currency.getInstance(currencyCode).getDefaultFractionDigits(), 0);
        String normalized = normalizeDecimalSeparators(compact, scale);
        BigDecimal amount;
        try {
            amount = new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Amount is invalid");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (amount.scale() > scale) {
            throw new IllegalArgumentException("Amount has too many decimal places for the wallet currency");
        }
        return amount.setScale(scale);
    }

    private String normalizeDecimalSeparators(String value, int currencyScale) {
        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            return comma > dot ? value.replace(".", "").replace(',', '.') : value.replace(",", "");
        }
        if (comma >= 0) {
            return value.replace(',', '.');
        }
        // VND has no fractional digits, so a dot-only amount is a grouped
        // statement value such as 12.000.000 rather than a decimal amount.
        return currencyScale == 0 ? value.replace(".", "") : value;
    }

    private String normalizeDescription(String value) {
        String description = value.trim().replaceAll("\\s+", " ");
        if (description.length() < 2 || description.length() > 255) {
            throw new IllegalArgumentException("Description must contain 2 to 255 characters");
        }
        return description;
    }

    private String normalizeNotes(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String notes = value.trim();
        if (notes.length() > 4000) {
            throw new IllegalArgumentException("Notes must contain at most 4000 characters");
        }
        return notes;
    }

    private UUID categoryIdFor(StatementImportRequest request, TransactionType transactionType) {
        return transactionType == TransactionType.INCOME ? request.incomeCategoryId() : request.expenseCategoryId();
    }

    private String idempotencyKey(UUID walletId, ParsedRow row) {
        String source = walletId + "|" + row.occurredAt() + "|" + row.transactionType() + "|"
                + row.amount().toPlainString() + "|" + row.description().toLowerCase(Locale.ROOT);
        try {
            return "statement:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private int count(List<StatementImportRow> rows, StatementImportRowStatus status) {
        return (int) rows.stream().filter(row -> row.status() == status).count();
    }

    private record ParsedRow(
            int rowNumber,
            TransactionType transactionType,
            BigDecimal amount,
            String description,
            String notes,
            Instant occurredAt,
            String error) {

        static ParsedRow valid(int rowNumber, TransactionType type, BigDecimal amount, String description, String notes, Instant occurredAt) {
            return new ParsedRow(rowNumber, type, amount, description, notes, occurredAt, null);
        }

        static ParsedRow invalid(int rowNumber, String error) {
            return new ParsedRow(rowNumber, null, null, null, null, null, error);
        }

        boolean isValid() {
            return error == null;
        }

        ParsedRow invalid(String reason) {
            return new ParsedRow(rowNumber, transactionType, amount, description, notes, occurredAt, reason);
        }

        StatementImportRow toResponse(StatementImportRowStatus status, String reason) {
            return new StatementImportRow(rowNumber, status, transactionType, amount, description, notes, occurredAt, reason);
        }
    }

    private static final class CsvReader {
        private CsvReader() {
        }

        static List<List<String>> parse(String text) {
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            char delimiter = delimiter(normalized);
            List<List<String>> rows = new ArrayList<>();
            List<String> row = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean inQuotes = false;
            for (int index = 0; index < normalized.length(); index++) {
                char current = normalized.charAt(index);
                if (current == '"') {
                    if (inQuotes && index + 1 < normalized.length() && normalized.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        inQuotes = !inQuotes;
                    }
                } else if (!inQuotes && current == delimiter) {
                    row.add(cell.toString());
                    cell.setLength(0);
                } else if (!inQuotes && current == '\n') {
                    row.add(cell.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    cell.setLength(0);
                } else {
                    cell.append(current);
                }
            }
            if (inQuotes) {
                throw new IllegalArgumentException("CSV contains an unclosed quoted field");
            }
            if (!row.isEmpty() || cell.length() > 0) {
                row.add(cell.toString());
                rows.add(row);
            }
            return rows;
        }

        private static char delimiter(String text) {
            String header = text.lines().findFirst().orElse("");
            return header.chars().filter(character -> character == ';').count()
                    > header.chars().filter(character -> character == ',').count() ? ';' : ',';
        }
    }
}
