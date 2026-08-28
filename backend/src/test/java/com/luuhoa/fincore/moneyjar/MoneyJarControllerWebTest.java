package com.luuhoa.fincore.moneyjar;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(MoneyJarController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MoneyJarControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MoneyJarService moneyJarService;

    @Test
    void transfersOnlyBetweenJarsOwnedByTheAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceJarId = UUID.randomUUID();
        UUID destinationJarId = UUID.randomUUID();
        TransferBetweenJarsRequest request = new TransferBetweenJarsRequest(
                sourceJarId, destinationJarId, new BigDecimal("250000"));
        when(moneyJarService.transfer(eq(userId), eq(request))).thenReturn(new JarTransferResponse(
                jar(sourceJarId, "Travel", "750000"),
                jar(destinationJarId, "Emergency", "250000"),
                new BigDecimal("250000")));

        mockMvc.perform(post("/api/v1/jars/transfers")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceJarId":"%s","destinationJarId":"%s","amount":250000}
                                """.formatted(sourceJarId, destinationJarId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceJar.id").value(sourceJarId.toString()))
                .andExpect(jsonPath("$.destinationJar.id").value(destinationJarId.toString()))
                .andExpect(jsonPath("$.amount").value(250000));

        verify(moneyJarService).transfer(userId, request);
    }

    @Test
    void rejectsInvalidTransferBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/jars/transfers")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(moneyJarService);
    }

    @Test
    void protectsJarTransfersFromUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/jars/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(moneyJarService);
    }

    private MoneyJarResponse jar(UUID id, String name, String balance) {
        return new MoneyJarResponse(
                id, name, "VND", new BigDecimal(balance), null, null, null, false,
                Instant.parse("2026-08-28T10:00:00Z"), Instant.parse("2026-08-28T10:00:00Z"));
    }
}
