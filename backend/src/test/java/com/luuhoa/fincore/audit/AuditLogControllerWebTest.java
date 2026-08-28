package com.luuhoa.fincore.audit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.luuhoa.fincore.config.SecurityConfig;
import com.luuhoa.fincore.shared.api.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuditLogControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    void returnsOnlyTheAuthenticatedUsersActivity() throws Exception {
        UUID userId = UUID.randomUUID();
        when(auditLogService.listForUser(userId, 20)).thenReturn(List.of(new AuditLogResponse(
                UUID.randomUUID(), "TRANSACTION_CREATED", "TRANSACTION", UUID.randomUUID(),
                Map.of("currency", "VND"), Instant.parse("2026-08-28T02:00:00Z"))));

        mockMvc.perform(get("/api/v1/activity").with(jwt().jwt(token -> token.subject(userId.toString())))
                        .queryParam("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("TRANSACTION_CREATED"))
                .andExpect(jsonPath("$[0].details.currency").value("VND"));

        verify(auditLogService).listForUser(eq(userId), eq(20));
    }

    @Test
    void protectsActivityHistory() throws Exception {
        mockMvc.perform(get("/api/v1/activity"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(auditLogService);
    }
}
