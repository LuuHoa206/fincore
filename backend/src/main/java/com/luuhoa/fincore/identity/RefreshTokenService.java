package com.luuhoa.fincore.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${app.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.repository = repository;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public IssuedRefreshToken issue(UserAccount user) {
        String value = newTokenValue();
        RefreshToken entity = new RefreshToken(user, hash(value), Instant.now().plus(refreshTokenTtl));
        return new IssuedRefreshToken(value, repository.save(entity));
    }

    public RefreshToken requireUsable(String tokenValue, Instant now) {
        RefreshToken token = repository.findByTokenHash(hash(tokenValue))
                .orElseThrow(() -> new InvalidRefreshTokenException());
        if (!token.isUsableAt(now)) {
            throw new InvalidRefreshTokenException();
        }
        return token;
    }

    public void revokeIfPresent(String tokenValue, Instant now) {
        repository.findByTokenHash(hash(tokenValue))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.revoke(now, null));
    }

    private String newTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record IssuedRefreshToken(String value, RefreshToken entity) {
    }
}
