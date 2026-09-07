package com.luuhoa.fincore.category;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategorySuggestionService categorySuggestionService;

    public CategoryController(CategoryService categoryService, CategorySuggestionService categorySuggestionService) {
        this.categoryService = categoryService;
        this.categorySuggestionService = categorySuggestionService;
    }

    @GetMapping
    List<CategoryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) CategoryType type) {
        return categoryService.list(userId(jwt), type);
    }

    @GetMapping("/suggestions")
    List<CategorySuggestionResponse> suggest(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam CategoryType type,
            @RequestParam String description) {
        return categorySuggestionService.suggest(userId(jwt), type, description);
    }

    @PostMapping
    ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(userId(jwt), request));
    }

    @PatchMapping("/{categoryId}")
    CategoryResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.update(userId(jwt), categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
        categoryService.archive(userId(jwt), categoryId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
