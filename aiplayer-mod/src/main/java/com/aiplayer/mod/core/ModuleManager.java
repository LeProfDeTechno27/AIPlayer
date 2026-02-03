package com.aiplayer.mod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleManager.class);

    private final Map<String, IBotModule> modules = new LinkedHashMap<>();
    private final List<String> enabledModules = new ArrayList<>();

    public void register(IBotModule module) {
        this.modules.put(module.getName(), module);

        if (!this.enabledModules.contains(module.getName())) {
            this.enabledModules.add(module.getName());
        }

        sortEnabledModules();
    }

    public void setEnabledModules(Collection<String> moduleNames) {
        this.enabledModules.clear();

        for (String moduleName : moduleNames) {
            if (this.modules.containsKey(moduleName)) {
                this.enabledModules.add(moduleName);
            } else {
                LOGGER.warn("Unknown module '{}' ignored from enable list", moduleName);
            }
        }

        sortEnabledModules();
    }

    public boolean enableModule(String moduleName) {
        if (!this.modules.containsKey(moduleName)) {
            return false;
        }

        if (!this.enabledModules.contains(moduleName)) {
            this.enabledModules.add(moduleName);
            sortEnabledModules();
        }

        return true;
    }

    public boolean disableModule(String moduleName) {
        return this.enabledModules.remove(moduleName);
    }

    public void initialize(BotContext context) {
        for (String moduleName : this.enabledModules) {
            this.modules.get(moduleName).init(context);
        }
    }

    public void tickEnabled() {
        for (String moduleName : this.enabledModules) {
            this.modules.get(moduleName).tick();
        }
    }

    public void shutdown() {
        for (String moduleName : this.enabledModules) {
            this.modules.get(moduleName).shutdown();
        }
    }

    public int size() {
        return this.modules.size();
    }

    public Map<String, IBotModule> getModules() {
        return Map.copyOf(this.modules);
    }

    public List<String> getEnabledModuleNames() {
        return List.copyOf(this.enabledModules);
    }

    public List<String> getRegisteredModuleNames() {
        return List.copyOf(this.modules.keySet());
    }

    private void sortEnabledModules() {
        this.enabledModules.sort(
            Comparator
                .comparingInt((String name) -> this.modules.get(name).getPriority())
                .reversed()
                .thenComparing(name -> name)
        );
    }
}