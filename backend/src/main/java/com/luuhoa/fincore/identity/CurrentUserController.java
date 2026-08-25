package com.luuhoa.fincore.identity;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserController {

    private final AuthService authService;

    public CurrentUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(UUID.fromString(jwt.getSubject()));
    }
}
