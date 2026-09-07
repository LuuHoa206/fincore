package com.luuhoa.fincore.transaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.luuhoa.fincore.category.CategorySuggestionResponse;
import com.luuhoa.fincore.category.CategorySuggestionService;
import com.luuhoa.fincore.category.CategoryType;
import com.luuhoa.fincore.identity.AuthService;
import com.luuhoa.fincore.identity.UserResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Small deterministic assistant for drafting a transaction from a short note.
 * It is intentionally explainable and performs no financial write.
 */
@Service
public class TransactionDraftAssistantService {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d[\\d.,]*)(?:\\s*)(k|nghin|ngan|tr|trieu)?(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final AuthService authService;
    private final CategorySuggestionService categorySuggestionService;
    private final Clock clock;

    @Autowired
    public TransactionDraftAssistantService(
            AuthService authService,
            CategorySuggestionService categorySuggestionService) {
        this(authService, categorySuggestionService, Clock.systemUTC());
    }

    TransactionDraftAssistantService(
            AuthService authService,
            CategorySuggestionService categorySuggestionService,
            Clock clock) {
        this.authService = authService;
        this.categorySuggestionService = categorySuggestionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TransactionDraftSuggestionResponse suggest(UUID userId, TransactionDraftSuggestionRequest request) {
        String text = request.text().trim().replaceAll("\\s+", " ");
        UserResponse user = authService.currentUser(userId);
        ZoneId zoneId = ZoneId.of(user.timeZone());
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        List<String> signals = new ArrayList<>();

        TransactionType type = suggestedType(text, request.currentTransactionType(), signals);
        BigDecimal amount = suggestedAmount(text, request.currency(), signals);
        LocalDate date = suggestedDate(text, today, signals);
        List<CategorySuggestionResponse> categories = categorySuggestionService.suggest(
                userId, categoryTypeFor(type), text);

        if (categories.isEmpty()) {
            signals.add("Chưa tìm thấy danh mục phù hợp từ nội dung");
        }
        return new TransactionDraftSuggestionResponse(
                text,
                amount,
                type,
                date,
                List.copyOf(signals),
                categories);
    }

    private TransactionType suggestedType(String text, TransactionType currentType, List<String> signals) {
        String normalized = normalize(text);
        boolean income = containsAny(normalized, "luong", "thuong", "nhan tien", "duoc tra", "ban hang", "hoan tien", "refund");
        boolean expense = containsAny(normalized, "mua", "an", "uong", "cafe", "grab", "taxi", "xang", "tien nha", "tien dien", "hoc phi", "thuoc");
        if (income && !expense) {
            signals.add("Nhận diện đây là khoản thu");
            return TransactionType.INCOME;
        }
        if (expense && !income) {
            signals.add("Nhận diện đây là khoản chi");
            return TransactionType.EXPENSE;
        }
        TransactionType fallback = currentType == TransactionType.INCOME ? TransactionType.INCOME : TransactionType.EXPENSE;
        signals.add("Giữ loại giao dịch đang chọn");
        return fallback;
    }

    private BigDecimal suggestedAmount(String text, String currencyCode, List<String> signals) {
        Matcher matcher = AMOUNT_PATTERN.matcher(normalize(text));
        if (!matcher.find()) {
            signals.add("Chưa tìm thấy số tiền rõ ràng");
            return null;
        }
        String rawNumber = matcher.group(1);
        String suffix = matcher.group(2);
        BigDecimal parsed = parseNumber(rawNumber, currencyCode, suffix != null);
        if (parsed == null || parsed.signum() <= 0) {
            signals.add("Số tiền chưa đủ rõ để áp dụng");
            return null;
        }
        if (suffix != null) {
            parsed = parsed.multiply(switch (suffix.toLowerCase(Locale.ROOT)) {
                case "k", "nghin", "ngan" -> BigDecimal.valueOf(1_000);
                case "tr", "trieu" -> BigDecimal.valueOf(1_000_000);
                default -> BigDecimal.ONE;
            });
        }
        int scale = Math.max(Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)).getDefaultFractionDigits(), 0);
        BigDecimal normalizedAmount = parsed.stripTrailingZeros();
        if (normalizedAmount.scale() > scale) {
            signals.add("Số tiền có phần lẻ không phù hợp với loại tiền tệ");
            return null;
        }
        BigDecimal result = normalizedAmount.setScale(scale);
        signals.add("Nhận diện số tiền " + result.stripTrailingZeros().toPlainString() + " " + currencyCode.toUpperCase(Locale.ROOT));
        return result;
    }

    private BigDecimal parseNumber(String rawNumber, String currencyCode, boolean hasMagnitudeSuffix) {
        String compact = rawNumber.replace(" ", "");
        int scale = Math.max(Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)).getDefaultFractionDigits(), 0);
        try {
            if (scale == 0) {
                if (hasMagnitudeSuffix && compact.matches("\\d+[.,]\\d+")) {
                    return new BigDecimal(compact.replace(',', '.'));
                }
                return new BigDecimal(compact.replace(".", "").replace(",", ""));
            }
            int comma = compact.lastIndexOf(',');
            int dot = compact.lastIndexOf('.');
            String normalized = comma >= 0 && dot >= 0
                    ? (comma > dot ? compact.replace(".", "").replace(',', '.') : compact.replace(",", ""))
                    : comma >= 0 ? compact.replace(',', '.') : compact;
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate suggestedDate(String text, LocalDate today, List<String> signals) {
        String normalized = normalize(text);
        if (normalized.contains("hom qua")) {
            signals.add("Nhận diện ngày hôm qua");
            return today.minusDays(1);
        }
        if (normalized.contains("hom nay")) {
            signals.add("Nhận diện ngày hôm nay");
            return today;
        }
        return null;
    }

    private CategoryType categoryTypeFor(TransactionType type) {
        return type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if ((" " + text + " ").contains(" " + phrase + " ")) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}.,]+", " ").trim().replaceAll(" +", " ");
    }
}
