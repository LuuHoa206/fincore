package com.luuhoa.fincore.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String preferredCurrency,
        String timeZone,
        Set<String> roles,
        Instant createdAt) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPreferredCurrency(),
                user.getTimeZone(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                user.getCreatedAt());
    }
}
