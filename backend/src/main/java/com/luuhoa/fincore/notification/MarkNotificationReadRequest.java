package com.luuhoa.fincore.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkNotificationReadRequest(
        @NotBlank @Size(max = 320) String notificationKey) {
}
