package com.luuhoa.fincore.identity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "preferred_currency", nullable = false, length = 3)
    private String preferredCurrency;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<UserRole> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, String displayName, String preferredCurrency, String timeZone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.preferredCurrency = preferredCurrency;
        this.timeZone = timeZone;
        this.roles.add(UserRole.USER);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Set<UserRole> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
