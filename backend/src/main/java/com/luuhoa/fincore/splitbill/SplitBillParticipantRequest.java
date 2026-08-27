package com.luuhoa.fincore.splitbill;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SplitBillParticipantRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 160) String contact,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal owedAmount) {
}
