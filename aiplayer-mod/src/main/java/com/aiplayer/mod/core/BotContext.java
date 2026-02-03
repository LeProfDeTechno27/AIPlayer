package com.aiplayer.mod.core;

import java.time.Instant;

public record BotContext(String phase, Instant startedAt) {
    public BotContext(String phase) {
        this(phase, Instant.now());
    }
}