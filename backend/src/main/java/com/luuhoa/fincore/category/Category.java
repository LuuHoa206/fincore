package com.luuhoa.fincore.category;

import java.time.Instant;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 20)
    private CategoryType categoryType;

    @Column(length = 50)
    private String icon;

    @Column(length = 20)
    private String color;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Category() {
    }

    public Category(UserAccount user, String name, CategoryType categoryType, String icon, String color) {
        this.user = user;
        this.name = name;
        this.categoryType = categoryType;
        this.icon = icon;
        this.color = color;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CategoryType getCategoryType() {
        return categoryType;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public boolean isArchived() {
        return archived;
    }

    public boolean isSystemCategory() {
        return user == null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateDetails(String name, String icon, String color) {
        this.name = name;
        this.icon = icon;
        this.color = color;
    }

    public void archive() {
        this.archived = true;
    }
}
