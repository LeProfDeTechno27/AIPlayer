package com.aiplayer.mod.core.ai;

import java.time.Instant;

public record BotGoal(
    String name,
    String description,
    String source,
    String status,
    Instant createdAt
) {
    public BotGoal {
        if (name == null || name.isBlank()) {
            name = "survival";
        }
        if (description == null) {
            description = "";
        }
        if (source == null || source.isBlank()) {
            source = "system";
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}