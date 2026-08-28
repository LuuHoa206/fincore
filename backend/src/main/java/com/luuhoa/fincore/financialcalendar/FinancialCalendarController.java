package com.luuhoa.fincore.financialcalendar;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/financial-calendar")
public class FinancialCalendarController {

    private final FinancialCalendarService financialCalendarService;

    public FinancialCalendarController(FinancialCalendarService financialCalendarService) {
        this.financialCalendarService = financialCalendarService;
    }

    @GetMapping
    FinancialCalendarResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String period) {
        return financialCalendarService.get(UUID.fromString(jwt.getSubject()), period);
    }
}
