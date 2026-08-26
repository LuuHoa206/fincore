package com.luuhoa.fincore.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("""
            select category from Category category
            left join category.user owner
            where category.archived = false
              and (:categoryType is null or category.categoryType = :categoryType)
              and (owner.id = :userId or owner is null)
            order by case when owner is null then 1 else 0 end, lower(category.name)
            """)
    List<Category> findVisibleByUserId(
            @Param("userId") UUID userId,
            @Param("categoryType") CategoryType categoryType);

    @Query("""
            select category from Category category
            left join category.user owner
            where category.id = :categoryId
              and category.archived = false
              and (owner.id = :userId or owner is null)
            """)
    Optional<Category> findAvailableByIdAndUserId(@Param("categoryId") UUID categoryId, @Param("userId") UUID userId);

    Optional<Category> findByIdAndUserIdAndArchivedFalse(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCaseAndCategoryType(UUID userId, String name, CategoryType categoryType);
}
