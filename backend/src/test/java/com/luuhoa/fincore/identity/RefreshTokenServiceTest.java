package com.luuhoa.fincore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Test
    void hashesTokensDeterministicallyWithoutStoringRawValue() {
        RefreshTokenService service = new RefreshTokenService(repository, Duration.ofDays(30));

        String first = service.hash("refresh-token-value");
        String second = service.hash("refresh-token-value");

        assertThat(first)
                .isEqualTo(second)
                .hasSize(64)
                .doesNotContain("refresh-token-value");
    }
}
