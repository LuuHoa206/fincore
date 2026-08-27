package com.luuhoa.fincore.category;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baseline, explainable category recommendation. It never writes financial data
 * and only evaluates categories visible to the current user.
 */
@Service
public class CategorySuggestionService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Set<String> IGNORED_CATEGORY_WORDS = Set.of("va", "cho", "khac");
    private static final List<SuggestionRule> RULES = List.of(
            new SuggestionRule(CategoryType.INCOME, Set.of("luong"), Set.of("luong", "salary", "payroll")),
            new SuggestionRule(CategoryType.INCOME, Set.of("thuong"), Set.of("thuong", "bonus", "tet")),
            new SuggestionRule(CategoryType.INCOME, Set.of("ban hang"), Set.of("ban hang", "don hang", "khach hang", "sales")),
            new SuggestionRule(CategoryType.INCOME, Set.of("dau tu"), Set.of("dau tu", "co tuc", "lai dau tu", "investment")),
            new SuggestionRule(CategoryType.EXPENSE, Set.of("an uong"), Set.of("com", "pho", "bun", "cafe", "ca phe", "tra sua", "nha hang", "do an", "do uong")),
            new SuggestionRule(CategoryType.EXPENSE, Set.of("di chuyen"), Set.of("grab", "taxi", "xang", "gui xe", "bus", "ve xe", "di chuyen")),
            new SuggestionRule(CategoryType.EXPENSE, Set.of("nha cua"), Set.of("tien nha", "thue nha", "tien tro", "sua nha", "noi that")),
            new SuggestionRule(CategoryType.EXPENSE, Set.of("hoa don"), Set.of("tien dien", "tien nuoc", "internet", "wifi", "dien thoai", "hoa don")),
            new SuggestionRule(CategoryType.EXPENSE, Set.of("suc khoe"), Set.of("thuoc", "kham", "benh vien", "nha khoa", "bao hiem", "suc khoe")),
            new SuggestionRule(CategoryType.EXPENSE, Set.of("giai tri"), Set.of("phim", "game", "netflix", "concert", "giai tri")),
            new SuggestionRule(CategoryType.EXPENSE, Set.of("mua sam"), Set.of("shopee", "lazada", "mua sam", "quan ao", "shopping")),
            new SuggestionRule(CategoryType.EXPENSE, Set.of("giao duc"), Set.of("hoc phi", "khoa hoc", "sach", "giao duc", "tuition")));

    private final CategoryRepository categoryRepository;

    public CategorySuggestionService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategorySuggestionResponse> suggest(UUID userId, CategoryType categoryType, String description) {
        String normalizedDescription = normalize(description);
        if (normalizedDescription.isBlank()) {
            throw new IllegalArgumentException("Transaction description is required for a category suggestion");
        }

        Set<String> descriptionWords = words(normalizedDescription);
        return categoryRepository.findVisibleByUserId(userId, categoryType).stream()
                .map(category -> score(category, normalizedDescription, descriptionWords))
                .filter(SuggestionCandidate::hasMatch)
                .sorted(Comparator.comparingInt(SuggestionCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.category().getName(), String.CASE_INSENSITIVE_ORDER))
                .limit(3)
                .map(SuggestionCandidate::toResponse)
                .toList();
    }

    private SuggestionCandidate score(Category category, String description, Set<String> descriptionWords) {
        String categoryName = normalize(category.getName());
        LinkedHashSet<String> matchedSignals = new LinkedHashSet<>();
        int score = 0;

        for (String word : words(categoryName)) {
            if (word.length() >= 3 && !IGNORED_CATEGORY_WORDS.contains(word) && descriptionWords.contains(word)) {
                score += 2;
                matchedSignals.add(word);
            }
        }

        for (SuggestionRule rule : RULES) {
            if (rule.appliesTo(category.getCategoryType(), categoryName)) {
                for (String keyword : rule.keywords()) {
                    if (containsPhrase(description, keyword)) {
                        score += 3;
                        matchedSignals.add(keyword);
                    }
                }
            }
        }

        return new SuggestionCandidate(category, score, List.copyOf(matchedSignals));
    }

    private boolean containsPhrase(String normalizedDescription, String phrase) {
        String normalizedPhrase = normalize(phrase);
        return (" " + normalizedDescription + " ").contains(" " + normalizedPhrase + " ");
    }

    private Set<String> words(String normalizedValue) {
        Set<String> result = new LinkedHashSet<>();
        for (String word : normalizedValue.split(" ")) {
            if (!word.isBlank()) {
                result.add(word);
            }
        }
        return result;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return NON_ALPHANUMERIC.matcher(withoutDiacritics.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim()
                .replaceAll(" +", " ");
    }

    private record SuggestionRule(CategoryType categoryType, Set<String> categoryAliases, Set<String> keywords) {

        boolean appliesTo(CategoryType candidateType, String normalizedCategoryName) {
            return categoryType == candidateType && categoryAliases.stream()
                    .map(alias -> alias.replace('đ', 'd'))
                    .anyMatch(normalizedCategoryName::contains);
        }
    }

    private record SuggestionCandidate(Category category, int score, List<String> matchedSignals) {

        boolean hasMatch() {
            return score > 0;
        }

        CategorySuggestionResponse toResponse() {
            String reason = "Khớp nội dung: " + String.join(", ", matchedSignals);
            return new CategorySuggestionResponse(
                    category.getId(),
                    category.getName(),
                    category.getCategoryType(),
                    category.isSystemCategory(),
                    reason,
                    score);
        }
    }
}
