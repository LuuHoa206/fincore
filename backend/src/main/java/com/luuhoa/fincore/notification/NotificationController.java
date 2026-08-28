package com.luuhoa.fincore.notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    List<FinancialNotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.list(userId(jwt));
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("count", notificationService.unreadCount(userId(jwt)));
    }

    @PatchMapping("/read")
    void markRead(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MarkNotificationReadRequest request) {
        notificationService.markRead(userId(jwt), request);
    }

    @PatchMapping("/unread")
    void markUnread(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MarkNotificationReadRequest request) {
        notificationService.markUnread(userId(jwt), request);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
