package com.luuhoa.fincore.category;

import java.util.List;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserAccountRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, UserAccountRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, CategoryType categoryType) {
        return categoryRepository.findVisibleByUserId(userId, categoryType).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CreateCategoryRequest request) {
        String name = normalizeName(request.name());
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndCategoryType(userId, name, request.categoryType())) {
            throw duplicateCategory();
        }

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        Category category = new Category(user, name, request.categoryType(), normalizeOptional(request.icon()), normalizeOptional(request.color()));
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID categoryId, UpdateCategoryRequest request) {
        Category category = requireOwnedActiveCategory(userId, categoryId);
        String name = normalizeName(request.name());
        if (!category.getName().equalsIgnoreCase(name)
                && categoryRepository.existsByUserIdAndNameIgnoreCaseAndCategoryType(userId, name, category.getCategoryType())) {
            throw duplicateCategory();
        }
        category.updateDetails(name, normalizeOptional(request.icon()), normalizeOptional(request.color()));
        return CategoryResponse.from(category);
    }

    @Transactional
    public void archive(UUID userId, UUID categoryId) {
        requireOwnedActiveCategory(userId, categoryId).archive();
    }

    /**
     * Public business boundary for modules that need to post an income or expense.
     */
    @Transactional(readOnly = true)
    public Category requireAvailableForTransaction(UUID userId, UUID categoryId, CategoryType expectedType) {
        Category category = categoryRepository.findAvailableByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category was not found"));
        if (category.getCategoryType() != expectedType) {
            throw new ConflictException("CATEGORY_TYPE_MISMATCH", "Category type does not match transaction type");
        }
        return category;
    }

    private Category requireOwnedActiveCategory(UUID userId, UUID categoryId) {
        return categoryRepository.findByIdAndUserIdAndArchivedFalse(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category was not found"));
    }

    private ConflictException duplicateCategory() {
        return new ConflictException("CATEGORY_ALREADY_EXISTS", "A category with this name and type already exists");
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
