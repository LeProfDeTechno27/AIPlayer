package com.aiplayer.mod.modules;

import com.aiplayer.mod.core.BotContext;
import com.aiplayer.mod.core.IBotModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class MineColoniesModule implements IBotModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MineColoniesModule.class);

    private final Map<String, String> state = new HashMap<>();

    @Override
    public String getName() {
        return "minecolonies";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public void init(BotContext context) {
        this.state.put("phase", context.phase());
        LOGGER.info("MineColonies module initialized for phase {}", context.phase());
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