package com.luuhoa.fincore.notification;

import java.time.Instant;

public record FinancialNotificationResponse(
        String key,
        NotificationPriority priority,
        String title,
        String message,
        String destination,
        Instant occurredAt,
        boolean read) {
}
