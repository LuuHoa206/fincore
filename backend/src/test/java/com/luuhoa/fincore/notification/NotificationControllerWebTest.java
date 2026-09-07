package com.luuhoa.fincore.notification;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NotificationControllerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationService notificationService;

    @Test
    void returnsNotificationsForTheAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationService.list(userId)).thenReturn(List.of(new FinancialNotificationResponse(
                "budget:test", NotificationPriority.WARNING, "Ngân sách sắp vượt hạn mức", "Ăn uống đã dùng 80%.",
                "/budgets", Instant.parse("2026-08-28T10:00:00Z"), false)));

        mockMvc.perform(get("/api/v1/notifications").with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("budget:test"))
                .andExpect(jsonPath("$[0].read").value(false));

        verify(notificationService).list(userId);
    }

    @Test
    void validatesReadRequestBeforeCallingTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void protectsNotificationsFromUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }
}
