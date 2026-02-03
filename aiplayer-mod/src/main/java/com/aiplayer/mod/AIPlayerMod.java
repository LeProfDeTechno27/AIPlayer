package com.aiplayer.mod;

import com.aiplayer.mod.commands.AIPlayerCommands;
import com.aiplayer.mod.core.AIPlayerRuntime;
import com.aiplayer.mod.core.ModuleManager;
import com.aiplayer.mod.modules.AE2Module;
import com.aiplayer.mod.modules.ComputerCraftModule;
import com.aiplayer.mod.modules.CreateModule;
import com.aiplayer.mod.modules.MineColoniesModule;
import com.aiplayer.mod.persistence.BotMemoryRepository;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Mod(ModMetadata.MOD_ID)
public class AIPlayerMod {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIPlayerMod.class);

    private final ModuleManager moduleManager;
    private final AIPlayerRuntime runtime;

    public AIPlayerMod() {
        this.moduleManager = new ModuleManager();
        registerDefaultModules();
        applyEnabledModulesOverride();

        BotMemoryRepository memoryRepository = new BotMemoryRepository(Path.of("aiplayer", "bot-memory.db"));
        memoryRepository.initializeSchema();

        this.runtime = new AIPlayerRuntime(this.moduleManager, memoryRepository);
        this.runtime.initialize();

        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("{} bootstrap complete. phase={}, modules={}", ModMetadata.MOD_ID, this.runtime.getPhase(), this.moduleManager.size());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AIPlayerCommands.register(event.getDispatcher(), this.runtime);
        LOGGER.info("/aiplayer command tree registered");
    }

    private void registerDefaultModules() {
        this.moduleManager.register(new MineColoniesModule());
        this.moduleManager.register(new AE2Module());
        this.moduleManager.register(new ComputerCraftModule());
        this.moduleManager.register(new CreateModule());
    }

    private void applyEnabledModulesOverride() {
        String rawValue = System.getenv("AIPLAYER_ENABLED_MODULES");
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }

        List<String> modules = Arrays.stream(rawValue.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();

        if (modules.isEmpty()) {
            LOGGER.warn("AIPLAYER_ENABLED_MODULES is set but empty after parsing: '{}'", rawValue);
            return;
        }

        this.moduleManager.setEnabledModules(modules);
        LOGGER.info("Enabled modules override applied: {}", this.moduleManager.getEnabledModuleNames());
    }
}