package com.luuhoa.fincore.monthlyreview;

import jakarta.validation.constraints.Size;

public record SaveMonthlyReviewRequest(
        @Size(max = 1500) String reflection,
        @Size(max = 500) String nextMonthFocus) {
}
