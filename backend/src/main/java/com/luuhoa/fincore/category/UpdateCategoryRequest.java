package com.luuhoa.fincore.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 50) @Pattern(regexp = "^[a-z0-9-]*$", message = "Icon must contain lowercase letters, numbers, or hyphens") String icon,
        @Size(max = 20) @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a hexadecimal color") String color) {
}
