package com.luuhoa.fincore.identity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Currency;
import java.util.Locale;

import com.luuhoa.fincore.shared.api.ConflictException;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.shared.api.UnauthorizedException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEFAULT_CURRENCY = "VND";
    private static final String DEFAULT_TIME_ZONE = "Asia/Ho_Chi_Minh";

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "An account already exists for this email");
        }

        String currency = normalizeCurrency(request.preferredCurrency());
        String timeZone = normalizeTimeZone(request.timeZone());
        UserAccount user = new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                currency,
                timeZone);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "An account already exists for this email");
        }
        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        Instant now = Instant.now();
        RefreshToken current = refreshTokenService.requireUsable(request.refreshToken(), now);
        UserAccount user = current.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            current.revoke(now, null);
            throw new UnauthorizedException("ACCOUNT_UNAVAILABLE", "The account is not active");
        }

        RefreshTokenService.IssuedRefreshToken replacement = refreshTokenService.issue(user);
        current.revoke(now, replacement.entity());
        return response(user, replacement.value());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revokeIfPresent(request.refreshToken(), Instant.now());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(java.util.UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
    }

    @Transactional
    public UserResponse updateProfile(java.util.UUID userId, UpdateProfileRequest request) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User account was not found"));
        user.updateProfile(
                request.displayName().trim(),
                normalizeCurrency(request.preferredCurrency()),
                normalizeTimeZone(request.timeZone()));
        return UserResponse.from(user);
    }

    private AuthResponse issueTokenPair(UserAccount user) {
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user);
        return response(user, refresh.value());
    }

    private AuthResponse response(UserAccount user, String refreshToken) {
        JwtTokenService.AccessToken access = jwtTokenService.issue(user);
        return new AuthResponse(
                access.value(),
                refreshToken,
                "Bearer",
                access.expiresIn(),
                UserResponse.from(user));
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCurrency(String requestedCurrency) {
        String currency = requestedCurrency == null || requestedCurrency.isBlank()
                ? DEFAULT_CURRENCY
                : requestedCurrency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency must be a valid ISO 4217 code");
        }
    }

    private String normalizeTimeZone(String requestedTimeZone) {
        String timeZone = requestedTimeZone == null || requestedTimeZone.isBlank()
                ? DEFAULT_TIME_ZONE
                : requestedTimeZone.trim();
        try {
            return ZoneId.of(timeZone).getId();
        } catch (ZoneRulesException exception) {
            throw new IllegalArgumentException("Time zone must be a valid IANA zone ID");
        }
    }
}
