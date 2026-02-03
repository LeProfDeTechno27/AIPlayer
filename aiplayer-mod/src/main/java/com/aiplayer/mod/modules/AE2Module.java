package com.aiplayer.mod.modules;

import com.aiplayer.mod.core.BotContext;
import com.aiplayer.mod.core.IBotModule;

import java.util.HashMap;
import java.util.Map;

public final class AE2Module implements IBotModule {
    private final Map<String, String> state = new HashMap<>();

    @Override
    public String getName() {
        return "ae2";
    }

    @Override
    public int getPriority() {
        return 90;
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