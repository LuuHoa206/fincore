package com.luuhoa.fincore.shared.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicHealthController {

    @GetMapping("/health")
    HealthResponse health() {
        return new HealthResponse("UP", "fincore-backend", Instant.now());
    }

    record HealthResponse(String status, String service, Instant timestamp) {
    }
}
