package com.aiplayer.mod.core;

import java.util.Map;

public interface IBotModule {
    String getName();

    int getPriority();

    void init(BotContext context);

    void tick();

    default void shutdown() {
    }

    Map<String, String> serializeState();

    void deserializeState(Map<String, String> state);
}