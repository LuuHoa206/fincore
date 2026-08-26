package com.luuhoa.fincore.allocationrule;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UpdateAllocationRuleRequest(
        @NotBlank @Size(max = 100) String name,
        boolean enabled,
        @NotEmpty @Size(max = 20) List<@Valid AllocationRuleItemRequest> items) {
}
