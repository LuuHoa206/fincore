package com.luuhoa.fincore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.audit.AuditLogService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, jwtTokenService, refreshTokenService, auditLogService);
    }

    @Test
    void recordsOnlySafeContextAfterSuccessfulPasswordLogin() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount("hoa@example.com", "encoded", "Luu Hoa", "VND", "Asia/Ho_Chi_Minh");
        setId(user, userId);
        when(userRepository.findByEmailIgnoreCase("hoa@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret-password", "encoded")).thenReturn(true);
        when(refreshTokenService.issue(user)).thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", null));
        when(jwtTokenService.issue(user)).thenReturn(new JwtTokenService.AccessToken("access-token", 900));

        AuthResponse response = service.login(new LoginRequest("hoa@example.com", "secret-password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(auditLogService).record(eq(user), eq("LOGIN_SUCCEEDED"), eq("USER"), eq(userId), eq(Map.of("method", "PASSWORD")));
    }

    private static void setId(UserAccount user, UUID id) throws Exception {
        Field field = UserAccount.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}
