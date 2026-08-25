package com.luuhoa.fincore.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
