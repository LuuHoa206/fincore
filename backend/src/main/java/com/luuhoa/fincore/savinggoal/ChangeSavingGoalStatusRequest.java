package com.luuhoa.fincore.savinggoal;

import jakarta.validation.constraints.NotNull;

public record ChangeSavingGoalStatusRequest(@NotNull SavingGoalStatus status) {
}
