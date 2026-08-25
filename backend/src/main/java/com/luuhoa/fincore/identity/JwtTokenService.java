package com.luuhoa.fincore.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.access-token-ttl}") Duration accessTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
    }

    public AccessToken issue(UserAccount user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("roles", roles)
                .build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new AccessToken(value, accessTokenTtl.toSeconds());
    }

    public record AccessToken(String value, long expiresIn) {
    }
}
