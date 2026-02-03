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

@Mod(ModMetadata.MOD_ID)
public class AIPlayerMod {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIPlayerMod.class);

    private final ModuleManager moduleManager;
    private final AIPlayerRuntime runtime;

    public AIPlayerMod() {
        this.moduleManager = new ModuleManager();
        registerDefaultModules();

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
}