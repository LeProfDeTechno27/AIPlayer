package com.aiplayer.mod.core.ai;

import java.util.List;

public record BotActionPlan(
    String goal,
    String rationale,
    List<BotActionStep> steps
) {
    public BotActionPlan {
        if (goal == null || goal.isBlank()) {
            goal = "survival";
        }
        if (rationale == null) {
            rationale = "";
        }
        if (steps == null) {
            steps = List.of();
        }
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }
}