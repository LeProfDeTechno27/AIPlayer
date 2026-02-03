package com.aiplayer.mod.modules;

import com.aiplayer.mod.core.BotContext;
import com.aiplayer.mod.core.IBotModule;

import java.util.HashMap;
import java.util.Map;

public final class CreateModule implements IBotModule {
    private final Map<String, String> state = new HashMap<>();

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public void init(BotContext context) {
        this.state.put("phase", context.phase());
    }

    @Override
    public void tick() {
    }

    @Override
    public Map<String, String> serializeState() {
        return Map.copyOf(this.state);
    }

    @Override
    public void deserializeState(Map<String, String> state) {
        this.state.clear();
        this.state.putAll(state);
    }
}