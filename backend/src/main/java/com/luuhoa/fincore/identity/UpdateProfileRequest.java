package com.luuhoa.fincore.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(min = 3, max = 3) String preferredCurrency,
        @NotBlank @Size(max = 60) String timeZone) {
}
