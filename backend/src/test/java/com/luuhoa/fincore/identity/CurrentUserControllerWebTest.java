package com.luuhoa.fincore.identity;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.luuhoa.fincore.config.SecurityConfig;
import com.luuhoa.fincore.shared.api.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CurrentUserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CurrentUserControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void updatesOnlyTheAuthenticatedUsersProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("Luu Hoa", "VND", "Asia/Ho_Chi_Minh");
        when(authService.updateProfile(userId, request)).thenReturn(new UserResponse(
                userId, "luu@example.com", "Luu Hoa", "VND", "Asia/Ho_Chi_Minh", Set.of("USER"), Instant.now()));

        mockMvc.perform(put("/api/v1/users/me")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Luu Hoa\",\"preferredCurrency\":\"VND\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Luu Hoa"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"));

        verify(authService).updateProfile(eq(userId), eq(request));
    }

    @Test
    void rejectsUnauthenticatedProfileUpdates() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Luu Hoa\",\"preferredCurrency\":\"VND\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }
}
