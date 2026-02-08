package com.aiplayer.mod.core;

import com.aiplayer.mod.entity.AIBotEntities;
import com.aiplayer.mod.entity.AIBotEntity;
import com.aiplayer.mod.integrations.AE2Bridge;
import com.aiplayer.mod.integrations.MineColoniesBridge;
import com.aiplayer.mod.integrations.OllamaClient;
import com.aiplayer.mod.persistence.BotMemoryRepository;
import com.aiplayer.mod.core.ai.ActionExecutor;
import com.aiplayer.mod.core.ai.BotActionPlan;
import com.aiplayer.mod.core.ai.BotActionStep;
import com.aiplayer.mod.core.ai.BotActionType;
import com.aiplayer.mod.core.ai.BotGoal;
import com.aiplayer.mod.core.ai.BotMemoryService;
import com.aiplayer.mod.core.ai.BotPerception;
import com.aiplayer.mod.core.ai.BotPlanner;
import com.aiplayer.mod.core.ai.FakePlayerController;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.time.Instant;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AIPlayerRuntime {
    private static final String MARKER_TAG = "aiplayer_bot_marker";
    private static final int AE2_QUEUE_BATCH_SIZE = 1;

    private final ModuleManager moduleManager;
    private final BotMemoryRepository memoryRepository;
    private final MineColoniesBridge mineColoniesBridge;
    private final AE2Bridge ae2Bridge;
    private final OllamaClient ollamaClient;
    private final BotMemoryService memoryService;
    private final FakePlayerController fakePlayerController;
    private final BotPlanner botPlanner;
    private final ActionExecutor actionExecutor;

    private String phase;
    private UUID botMarkerEntityId;
    private UUID botEntityId;
    private String botName = "AIPlayer Bot";
    private FakePlayer botFakePlayer;
    private int botHunger = 20;
    private Instant lastHungerAt;
    private Instant lastHealAt;
    private Instant lastHarvestAt;
    private Instant lastMineAt;
    private Instant lastCraftAt;
    private Instant lastSleepAt;
    private Instant lastSurvivalAt;
    private Instant lastDecisionStatsAt;
    private Instant lastTickLogAt;
    private final double maxMspt = resolveMaxMspt();
    private Instant lastMsptLogAt;
    private final Duration recipeCacheTtl = resolveRecipeCacheTtl();
    private final Map<String, CachedRecipeRequest> recipeCache = new HashMap<>();
    private long survivalMinutes;
    private boolean survivalDayLogged;
    private String currentObjective;
    private int botXp;
    private BotActionPlan currentPlan;
    private BotPerception lastPerception;
    private BotGoal activeGoal;
    private Instant lastPerceptionAt;
    private Instant lastDecisionAt;
    private int decisionTickCounter;
    private int decisionsMade;
    private int decisionSkips;
    private boolean paused;
    private boolean debug = resolveDebug();
    private final int decisionIntervalTicks = resolveDecisionIntervalTicks();
    private final int actionTimeoutTicks = resolveActionTimeoutTicks();
    private final int maxPlanSteps = resolveMaxPlanSteps();

    public AIPlayerRuntime(ModuleManager moduleManager, BotMemoryRepository memoryRepository) {
        this.moduleManager = moduleManager;
        this.memoryRepository = memoryRepository;
        this.mineColoniesBridge = new MineColoniesBridge();
        this.ae2Bridge = new AE2Bridge();
        this.ollamaClient = new OllamaClient(resolveOllamaUrl(), resolveOllamaModel());
        this.memoryService = new BotMemoryService(this.memoryRepository);
        this.fakePlayerController = new FakePlayerController();
        this.botPlanner = new BotPlanner(this.ollamaClient);
        this.actionExecutor = new ActionExecutor(this.fakePlayerController, this.memoryService);
        this.phase = this.memoryRepository.loadCurrentPhase().orElse("bootstrap");
        this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
        this.botXp = this.memoryRepository.loadBotXp().orElse(0);
        this.activeGoal = this.memoryService.loadActiveGoal().orElse(null);
    }

    public void initialize() {
        boolean envOverride = hasEnabledModulesEnvOverride();
        if (!envOverride) {
            this.memoryRepository.loadEnabledModules()
                .filter(modules -> !modules.isEmpty())
                .ifPresent(this.moduleManager::setEnabledModules);
        }

        this.moduleManager.initialize(new BotContext(this.phase));
        this.memoryRepository.saveEnabledModules(this.moduleManager.getEnabledModuleNames());
    }

    public String getPhase() {
        return this.phase;
    }

    public String getCurrentObjective() {
        return this.currentObjective;
    }

    public int getBotXp() {
        return this.botXp;
    }

    public String getBotSkillTier() {
        if (this.botXp >= 1000) {
            return "expert";
        }
        if (this.botXp >= 500) {
            return "artisan";
        }
        if (this.botXp >= 100) {
            return "apprenti";
        }
        return "novice";
    }

    public int addBotXp(int delta) {
        int next = Math.max(0, this.botXp + delta);
        this.botXp = next;
        this.memoryRepository.saveBotXp(next);
        this.memoryRepository.recordAction("bot-xp", "value=" + next + " delta=" + delta);
        return next;
    }

    public int setBotXp(int value) {
        int next = Math.max(0, value);
        int delta = next - this.botXp;
        this.botXp = next;
        this.memoryRepository.saveBotXp(next);
        this.memoryRepository.recordAction("bot-xp-set", "value=" + next + " delta=" + delta);
        return next;
    }
    public void setPhase(String nextPhase) {
        this.phase = nextPhase;
        this.memoryRepository.saveCurrentPhase(nextPhase);
        this.memoryRepository.recordAction("set-phase", nextPhase);
    }

    public void tickOnce(ServerLevel level) {
        Instant now = Instant.now();
        this.moduleManager.tickEnabled();
        processBotTaskLifecycle();
        processPendingAe2CraftRequests();
        AIBotEntity bot = getTrackedBot(level);
        if (bot == null) {
            recordTickLog(now);
            return;
        }

        ensureFakePlayer(level, bot);
        actionExecutor.tick(level, bot, botName);
        if (currentPlan != null && actionExecutor.isIdle()) {
            currentPlan = null;
        }
        processSurvivalLoop(level);

        if (paused) {
            recordDecisionStats(now);
            recordTickLog(now);
            return;
        }

        if (shouldThrottleForMspt(level, now)) {
            decisionSkips++;
            recordDecisionStats(now);
            recordTickLog(now);
            return;
        }

        decisionTickCounter++;
        if (decisionTickCounter < decisionIntervalTicks) {
            recordDecisionStats(now);
            recordTickLog(now);
            return;
        }
        decisionTickCounter = 0;

        if (!actionExecutor.isIdle()) {
            decisionSkips++;
            recordDecisionStats(now);
            recordTickLog(now);
            return;
        }

        lastPerception = collectPerception(level, bot);
        lastPerceptionAt = now;
        if (lastPerception != null) {
            memoryService.recordInventorySnapshot(lastPerception.inventorySummary());
            recordKnownLocations(level, lastPerception.nearbyBlocks());
        }

        BotGoal goal = resolveGoal(lastPerception);
        if (goal == null) {
            decisionSkips++;
            recordDecisionStats(now);
            recordTickLog(now);
            return;
        }

        activeGoal = goal;
        String memorySummary = buildMemorySummary();
        BotActionPlan plan = botPlanner.plan(lastPerception, goal, memorySummary, maxPlanSteps);
        BotActionPlan sanitized = sanitizePlan(plan);
        if (sanitized == null || sanitized.isEmpty()) {
            BotActionPlan fallback = buildHeuristicPlan(goal, lastPerception);
            if (fallback == null || fallback.isEmpty()) {
                decisionSkips++;
                recordDecisionStats(now);
                recordTickLog(now);
                return;
            }
            sanitized = fallback;
        }

        currentPlan = sanitized;
        actionExecutor.setPlan(sanitized);
        decisionsMade++;
        lastDecisionAt = now;
        this.memoryRepository.recordAction("bot-plan", "goal=" + sanitized.goal() + " steps=" + sanitized.steps().size());

        recordDecisionStats(now);
        recordTickLog(now);
    }

    private boolean shouldThrottleForMspt(ServerLevel level, Instant now) {
        if (maxMspt <= 0.0d) {
            return false;
        }
        double mspt = resolveAverageTickTimeMs(level);
        if (mspt <= maxMspt) {
            return false;
        }
        if (lastMsptLogAt == null || Duration.between(lastMsptLogAt, now).compareTo(Duration.ofMinutes(1)) >= 0) {
            String formatted = String.format(Locale.ROOT, "%.2f", mspt);
            this.memoryRepository.recordAction("bot-brain-mspt-throttle", "mspt=" + formatted + " limit=" + maxMspt);
            lastMsptLogAt = now;
        }
        return true;
    }


    private double resolveAverageTickTimeMs(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return 0.0d;
        }
        Object server = level.getServer();
        try {
            var method = server.getClass().getMethod("getAverageTickTime");
            Object value = method.invoke(server);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            var method = server.getClass().getMethod("getAverageTickTimeNanos");
            Object value = method.invoke(server);
            if (value instanceof Number number) {
                return number.doubleValue() / 1_000_000.0d;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 0.0d;
    }

    private void recordDecisionStats(Instant now) {
        if (lastDecisionStatsAt != null && Duration.between(lastDecisionStatsAt, now).compareTo(Duration.ofMinutes(5)) < 0) {
            return;
        }
        String lastDecision = (lastDecisionAt == null) ? "never" : Duration.between(lastDecisionAt, now).toSeconds() + "s";
        this.memoryRepository.recordAction(
            "bot-planner-stats",
            "decisions=" + decisionsMade + " skipped=" + decisionSkips + " last=" + lastDecision
        );
        lastDecisionStatsAt = now;
    }

    private void recordTickLog(Instant now) {
        if (lastTickLogAt != null && Duration.between(lastTickLogAt, now).compareTo(Duration.ofMinutes(1)) < 0) {
            return;
        }
        this.memoryRepository.recordAction("tick", "ok");
        lastTickLogAt = now;
    }

    private void ensureFakePlayer(ServerLevel level, AIBotEntity bot) {
        if (botFakePlayer == null || !botFakePlayer.getGameProfile().getName().equals(botName)) {
            botFakePlayer = fakePlayerController.getOrCreate(level, botName);
        }
        if (botFakePlayer != null) {
            fakePlayerController.syncPosition(botFakePlayer, bot.position());
        }
    }

    private BotPerception collectPerception(ServerLevel level, AIBotEntity bot) {
        FakePlayer player = botFakePlayer;
        float health = player != null ? player.getHealth() : bot.getHealth();
        float maxHealth = player != null ? player.getMaxHealth() : bot.getMaxHealth();
        int hunger = player != null ? player.getFoodData().getFoodLevel() : botHunger;
        int xp = botXp;
        BlockPos pos = bot.blockPosition();
        String dimension = level.dimension().location().toString();
        String biome = resolveBiomeName(level, pos);
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;
        List<String> nearbyEntities = scanNearbyEntities(level, pos, 12, 8);
        List<String> nearbyBlocks = scanNearbyBlocks(level, pos, 6, 10);
        List<String> inventorySummary = summarizeInventory(player);
        List<String> equipmentSummary = summarizeEquipment(player);
        return new BotPerception(
            phase,
            currentObjective,
            health,
            maxHealth,
            hunger,
            xp,
            pos,
            dimension,
            biome,
            timeOfDay,
            day,
            nearbyEntities,
            nearbyBlocks,
            inventorySummary,
            equipmentSummary
        );
    }

    private String resolveBiomeName(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return "unknown";
        }
        Holder<Biome> holder = level.getBiome(pos);
        return holder.unwrapKey()
            .map(key -> key.location().toString())
            .orElse("unknown");
    }

    private BotGoal resolveGoal(BotPerception perception) {
        Optional<BotGoal> manual = memoryService.loadActiveGoal();
        if (manual.isPresent()) {
            return manual.get();
        }
        if (currentObjective != null && !"none".equalsIgnoreCase(currentObjective)) {
            return new BotGoal("task", currentObjective, "task", "ACTIVE", Instant.now());
        }
        if (perception == null) {
            return null;
        }
        return resolveHeuristicGoal(perception);
    }

    private BotGoal resolveHeuristicGoal(BotPerception perception) {
        List<String> inventory = perception.inventorySummary();
        if (perception.hunger() <= 6) {
            return new BotGoal("find_food", "Trouver et manger de la nourriture", "heuristic", "ACTIVE", Instant.now());
        }
        if (!inventoryContains(inventory, "minecraft:crafting_table")) {
            return new BotGoal("starter_base", "Fabriquer une table de craft et un four", "heuristic", "ACTIVE", Instant.now());
        }
        if (!inventoryContains(inventory, "minecraft:furnace")) {
            return new BotGoal("starter_base", "Installer un four pour cuire et fondre", "heuristic", "ACTIVE", Instant.now());
        }
        if (!hasBed(inventory)) {
            return new BotGoal("starter_base", "Obtenir un lit pour passer la nuit", "heuristic", "ACTIVE", Instant.now());
        }
        if (!inventoryContains(inventory, "minecraft:chest")) {
            return new BotGoal("starter_base", "Installer un coffre pour stocker", "heuristic", "ACTIVE", Instant.now());
        }
        if (!inventoryContains(inventory, "minecraft:wooden_pickaxe")) {
            return new BotGoal("starter_tools", "Fabriquer des outils en bois", "heuristic", "ACTIVE", Instant.now());
        }
        if (!inventoryContains(inventory, "minecraft:stone_pickaxe")) {
            return new BotGoal("upgrade_tools", "Passer aux outils en pierre", "heuristic", "ACTIVE", Instant.now());
        }
        if (!inventoryContains(inventory, "minecraft:iron_pickaxe")) {
            return new BotGoal("upgrade_tools", "Obtenir des outils en fer", "heuristic", "ACTIVE", Instant.now());
        }
        if (!inventoryContains(inventory, "minecraft:diamond_pickaxe")) {
            return new BotGoal("mine_diamond", "Chercher du diamant", "heuristic", "ACTIVE", Instant.now());
        }
        return new BotGoal("explore", "Explorer et collecter des ressources", "heuristic", "ACTIVE", Instant.now());
    }

    private boolean inventoryContains(List<String> inventory, String itemId) {
        if (inventory == null || itemId == null) {
            return false;
        }
        for (String entry : inventory) {
            if (entry.startsWith(itemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBed(List<String> inventory) {
        if (inventory == null) {
            return false;
        }
        for (String entry : inventory) {
            if (entry.contains("_bed")) {
                return true;
            }
        }
        return false;
    }

    private BotActionPlan sanitizePlan(BotActionPlan plan) {
        if (plan == null) {
            return null;
        }
        if (plan.steps().isEmpty()) {
            return plan;
        }
        List<BotActionStep> steps = new java.util.ArrayList<>();
        for (BotActionStep step : plan.steps()) {
            int timeout = step.timeoutTicks();
            if (timeout <= 0 || timeout > actionTimeoutTicks) {
                timeout = actionTimeoutTicks;
            }
            int count = step.count() <= 0 ? 1 : step.count();
            steps.add(new BotActionStep(step.type(), step.target(), step.itemId(), count, timeout));
        }
        return new BotActionPlan(plan.goal(), plan.rationale(), steps);
    }

    private String buildMemorySummary() {
        StringBuilder builder = new StringBuilder();
        if (activeGoal != null) {
            appendMemoryPart(builder, "goal=" + activeGoal.name());
        }
        memoryService.loadLatestInventorySummary()
            .ifPresent(summary -> appendMemoryPart(builder, "inventory=" + summary));

        List<BotMemoryRepository.KnownLocationRecord> locations = memoryService.loadKnownLocations(5);
        if (!locations.isEmpty()) {
            appendMemoryPart(builder, "locations=" + formatLocations(locations));
        }

        List<BotMemoryRepository.ActionHistoryRecord> actions = memoryService.loadRecentActionHistory(5);
        if (!actions.isEmpty()) {
            appendMemoryPart(builder, "recent=" + formatActions(actions));
        }

        String summary = builder.toString();
        if (summary.length() > 1000) {
            return summary.substring(0, 1000);
        }
        return summary;
    }

    private void appendMemoryPart(StringBuilder builder, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" | ");
        }
        builder.append(part);
    }

    private String formatLocations(List<BotMemoryRepository.KnownLocationRecord> locations) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (BotMemoryRepository.KnownLocationRecord location : locations) {
            if (count++ >= 5) {
                break;
            }
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(location.label())
                .append("@")
                .append(location.x())
                .append(",")
                .append(location.y())
                .append(",")
                .append(location.z());
        }
        return builder.toString();
    }

    private String formatActions(List<BotMemoryRepository.ActionHistoryRecord> actions) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (BotMemoryRepository.ActionHistoryRecord action : actions) {
            if (count++ >= 5) {
                break;
            }
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(action.actionType());
            if (action.itemId() != null && !action.itemId().isBlank()) {
                builder.append(":").append(action.itemId());
            }
            builder.append(action.success() ? ":ok" : ":fail");
        }
        return builder.toString();
    }

    private BotActionPlan buildHeuristicPlan(BotGoal goal, BotPerception perception) {
        if (goal == null || perception == null) {
            return null;
        }
        List<String> inventory = perception.inventorySummary();
        List<BotActionStep> steps = new java.util.ArrayList<>();
        String goalName = goal.name() == null ? "" : goal.name();

        switch (goalName) {
            case "starter_base" -> {
                addCraftIfMissing(steps, inventory, "minecraft:crafting_table");
                addCraftIfMissing(steps, inventory, "minecraft:furnace");
                addCraftIfMissing(steps, inventory, "minecraft:chest");
                if (!hasBed(inventory)) {
                    steps.add(new BotActionStep(BotActionType.CRAFT, null, "minecraft:white_bed", 1, actionTimeoutTicks));
                }
            }
            case "starter_tools" -> addCraftIfMissing(steps, inventory, "minecraft:wooden_pickaxe");
            case "upgrade_tools" -> {
                if (!inventoryContains(inventory, "minecraft:stone_pickaxe")) {
                    addCraftIfMissing(steps, inventory, "minecraft:stone_pickaxe");
                } else if (!inventoryContains(inventory, "minecraft:iron_pickaxe")) {
                    addCraftIfMissing(steps, inventory, "minecraft:iron_pickaxe");
                } else if (!inventoryContains(inventory, "minecraft:diamond_pickaxe")) {
                    addCraftIfMissing(steps, inventory, "minecraft:diamond_pickaxe");
                }
            }
            case "find_food" -> addCraftIfMissing(steps, inventory, "minecraft:bread");
            case "mine_diamond" -> addMoveMineStep(steps, perception, List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
            default -> addMoveMineStep(
                steps,
                perception,
                List.of(
                    "minecraft:iron_ore",
                    "minecraft:deepslate_iron_ore",
                    "minecraft:coal_ore",
                    "minecraft:deepslate_coal_ore",
                    "minecraft:oak_log",
                    "minecraft:spruce_log",
                    "minecraft:birch_log",
                    "minecraft:jungle_log",
                    "minecraft:acacia_log",
                    "minecraft:dark_oak_log",
                    "minecraft:mangrove_log"
                )
            );
        }

        if (steps.isEmpty()) {
            steps.add(new BotActionStep(BotActionType.WAIT, null, "", 1, Math.min(80, actionTimeoutTicks)));
        }
        return new BotActionPlan(goal.name(), "heuristic", steps);
    }

    private void addCraftIfMissing(List<BotActionStep> steps, List<String> inventory, String itemId) {
        if (inventoryContains(inventory, itemId)) {
            return;
        }
        steps.add(new BotActionStep(BotActionType.CRAFT, null, itemId, 1, actionTimeoutTicks));
    }

    private void addMoveMineStep(List<BotActionStep> steps, BotPerception perception, List<String> preferredIds) {
        BlockPos target = findTargetBlock(perception, preferredIds);
        if (target == null) {
            return;
        }
        steps.add(new BotActionStep(BotActionType.MOVE, target, "", 1, actionTimeoutTicks));
        steps.add(new BotActionStep(BotActionType.MINE, target, "", 1, actionTimeoutTicks));
    }

    private BlockPos findTargetBlock(BotPerception perception, List<String> preferredIds) {
        if (perception == null || perception.nearbyBlocks() == null || perception.nearbyBlocks().isEmpty()) {
            return null;
        }
        if (preferredIds != null && !preferredIds.isEmpty()) {
            for (String entry : perception.nearbyBlocks()) {
                int split = entry.indexOf('@');
                if (split <= 0) {
                    continue;
                }
                String blockId = entry.substring(0, split);
                if (!preferredIds.contains(blockId)) {
                    continue;
                }
                BlockPos pos = parseBlockPos(entry.substring(split + 1));
                if (pos != null) {
                    return pos;
                }
            }
        }

        for (String entry : perception.nearbyBlocks()) {
            int split = entry.indexOf('@');
            if (split <= 0) {
                continue;
            }
            BlockPos pos = parseBlockPos(entry.substring(split + 1));
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    private List<String> scanNearbyEntities(ServerLevel level, BlockPos pos, int radius, int limit) {
        if (pos == null) {
            return List.of();
        }
        AABB box = new AABB(pos).inflate(radius);
        List<Entity> entities = level.getEntities((Entity) null, box, entity -> !(entity instanceof AIBotEntity) && !(entity instanceof FakePlayer));
        List<String> results = new java.util.ArrayList<>();
        for (Entity entity : entities) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (key != null) {
                results.add(key.toString());
            }
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private List<String> scanNearbyBlocks(ServerLevel level, BlockPos pos, int radius, int limit) {
        List<String> results = new java.util.ArrayList<>();
        if (pos == null) {
            return results;
        }
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (!isInterestingBlock(state.getBlock())) {
                        continue;
                    }
                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    results.add(blockId + "@" + checkPos.getX() + "," + checkPos.getY() + "," + checkPos.getZ());
                    if (results.size() >= limit) {
                        return results;
                    }
                }
            }
        }
        return results;
    }

    private boolean isInterestingBlock(Block block) {
        if (block.defaultBlockState().is(BlockTags.LOGS)) {
            return true;
        }
        if (block.defaultBlockState().is(BlockTags.BEDS)) {
            return true;
        }
        return block == Blocks.CRAFTING_TABLE
            || block == Blocks.FURNACE
            || block == Blocks.BLAST_FURNACE
            || block == Blocks.CHEST
            || block == Blocks.BARREL
            || block == Blocks.IRON_ORE
            || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.COAL_ORE
            || block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.DIAMOND_ORE
            || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.GOLD_ORE
            || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.EMERALD_ORE
            || block == Blocks.REDSTONE_ORE
            || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.LAPIS_ORE
            || block == Blocks.DEEPSLATE_LAPIS_ORE;
    }

    private List<String> summarizeInventory(FakePlayer player) {
        if (player == null) {
            return List.of();
        }
        List<String> summary = new java.util.ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            summary.add(itemId + "x" + stack.getCount());
            if (summary.size() >= 10) {
                break;
            }
        }
        return summary;
    }

    private List<String> summarizeEquipment(FakePlayer player) {
        if (player == null) {
            return List.of();
        }
        List<String> summary = new java.util.ArrayList<>();
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty()) {
            summary.add("main=" + BuiltInRegistries.ITEM.getKey(main.getItem()));
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty()) {
            summary.add("off=" + BuiltInRegistries.ITEM.getKey(off.getItem()));
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot != net.minecraft.world.entity.EquipmentSlot.HEAD
                && slot != net.minecraft.world.entity.EquipmentSlot.CHEST
                && slot != net.minecraft.world.entity.EquipmentSlot.LEGS
                && slot != net.minecraft.world.entity.EquipmentSlot.FEET) {
                continue;
            }
            ItemStack armor = player.getItemBySlot(slot);
            if (!armor.isEmpty()) {
                summary.add(slot.getName() + "=" + BuiltInRegistries.ITEM.getKey(armor.getItem()));
            }
        }
        return summary;
    }

    private void recordKnownLocations(ServerLevel level, List<String> nearbyBlocks) {
        if (nearbyBlocks == null || nearbyBlocks.isEmpty()) {
            return;
        }
        String dimension = level.dimension().location().toString();
        for (String entry : nearbyBlocks) {
            int split = entry.indexOf('@');
            if (split <= 0) {
                continue;
            }
            String blockId = entry.substring(0, split);
            BlockPos pos = parseBlockPos(entry.substring(split + 1));
            if (pos == null) {
                continue;
            }
            String label = switch (blockId) {
                case "minecraft:crafting_table" -> "crafting_table";
                case "minecraft:furnace", "minecraft:blast_furnace" -> "furnace";
                case "minecraft:chest", "minecraft:barrel" -> "storage";
                case "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore" -> "diamond_ore";
                case "minecraft:iron_ore", "minecraft:deepslate_iron_ore" -> "iron_ore";
                default -> null;
            };
            if (label != null) {
                memoryService.recordKnownLocation(label, pos, dimension);
            }
        }
    }

    private BlockPos parseBlockPos(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    public boolean enableModule(String moduleName) {
        boolean enabled = this.moduleManager.enableModule(moduleName);
        if (enabled) {
            this.memoryRepository.recordAction("enable-module", moduleName);
            this.memoryRepository.saveEnabledModules(this.moduleManager.getEnabledModuleNames());
        }
        return enabled;
    }

    public boolean disableModule(String moduleName) {
        boolean disabled = this.moduleManager.disableModule(moduleName);
        if (disabled) {
            this.memoryRepository.recordAction("disable-module", moduleName);
            this.memoryRepository.saveEnabledModules(this.moduleManager.getEnabledModuleNames());
        }
        return disabled;
    }

    public List<String> getEnabledModules() {
        return this.moduleManager.getEnabledModuleNames();
    }


    public int disableAllModules() {
        int before = this.moduleManager.getEnabledModuleNames().size();
        this.moduleManager.setEnabledModules(List.of());
        this.memoryRepository.saveEnabledModules(this.moduleManager.getEnabledModuleNames());
        this.memoryRepository.recordAction("disable-all-modules", "count=" + before);
        return before;
    }

    public boolean isModuleRegistered(String moduleName) {
        return this.moduleManager.getRegisteredModuleNames().contains(moduleName);
    }

    public boolean isModuleEnabled(String moduleName) {
        return this.moduleManager.getEnabledModuleNames().contains(moduleName);
    }

    public boolean isModulesEnvOverrideActive() {
        return hasEnabledModulesEnvOverride();
    }

    public Optional<List<String>> getStoredEnabledModules() {
        return this.memoryRepository.loadEnabledModules();
    }
    public List<String> getRegisteredModules() {
        return this.moduleManager.getRegisteredModuleNames();
    }




    public boolean clearStoredEnabledModules() {
        boolean cleared = this.memoryRepository.clearEnabledModules();
        if (cleared) {
            this.memoryRepository.recordAction("clear-modules-storage", "enabled_modules");
        }
        return cleared;
    }

    public String reconcileModulesWithStorage() {
        if (hasEnabledModulesEnvOverride()) {
            return "env-override";
        }

        List<String> enabled = this.moduleManager.getEnabledModuleNames();
        Optional<List<String>> storedOptional = this.memoryRepository.loadEnabledModules();

        if (storedOptional.isEmpty()) {
            this.memoryRepository.saveEnabledModules(enabled);
            this.memoryRepository.recordAction("reconcile-modules", "saved-active");
            return "saved-active";
        }

        List<String> stored = storedOptional.get();
        if (enabled.equals(stored)) {
            return "in-sync";
        }

        this.moduleManager.setEnabledModules(stored);
        this.memoryRepository.recordAction("reconcile-modules", "reloaded-from-storage");
        return "reloaded-from-storage";
    }
    public void saveModulesToStorage() {
        this.memoryRepository.saveEnabledModules(this.moduleManager.getEnabledModuleNames());
        this.memoryRepository.recordAction("save-modules", String.join(",", this.moduleManager.getEnabledModuleNames()));
    }
    public boolean reloadModulesFromStorage() {
        if (hasEnabledModulesEnvOverride()) {
            return false;
        }

        Optional<List<String>> loaded = this.memoryRepository.loadEnabledModules();
        if (loaded.isEmpty()) {
            return false;
        }

        this.moduleManager.setEnabledModules(loaded.get());
        this.memoryRepository.recordAction("reload-modules", String.join(",", this.moduleManager.getEnabledModuleNames()));
        return true;
    }
    public void resetModules() {
        this.moduleManager.setEnabledModules(this.moduleManager.getRegisteredModuleNames());
        this.memoryRepository.saveEnabledModules(this.moduleManager.getEnabledModuleNames());
        this.memoryRepository.recordAction("reset-modules", String.join(",", this.moduleManager.getEnabledModuleNames()));
    }

    public long queueBotTask(String objective, String requestedBy) {
        long taskId = this.memoryRepository.enqueueBotTask(objective, requestedBy);
        if (taskId > 0) {
            this.memoryRepository.recordAction("bot-task-enqueue", "id=" + taskId + " by=" + requestedBy + " objective=" + objective);
        }
        return taskId;
    }

    public List<BotMemoryRepository.BotTask> getOpenBotTasks(int limit) {
        return this.memoryRepository.loadOpenBotTasks(limit);
    }

    public List<BotMemoryRepository.BotTask> getBotTasks(int limit, boolean includeClosed) {
        return includeClosed ? this.memoryRepository.loadBotTasks(limit) : this.memoryRepository.loadOpenBotTasks(limit);
    }

    public int countOpenBotTasks() {
        return this.memoryRepository.countOpenBotTasks();
    }

    public int countBotTasks(boolean includeClosed) {
        return includeClosed ? this.memoryRepository.countBotTasks() : this.memoryRepository.countOpenBotTasks();
    }

    public List<BotMemoryRepository.BotTask> getBotTasksByStatus(String status, int limit) {
        return this.memoryRepository.loadBotTasksByStatus(status, limit);
    }

    public int countBotTasksByStatus(String status) {
        return this.memoryRepository.countBotTasksByStatus(status);
    }

    public Optional<BotMemoryRepository.BotTask> getBotTaskById(long taskId) {
        return this.memoryRepository.loadBotTaskById(taskId);
    }


    public boolean deleteBotTask(long taskId) {
        boolean deleted = this.memoryRepository.deleteBotTask(taskId);
        if (deleted) {
            this.memoryRepository.recordAction("bot-task-delete", "id=" + taskId);
            this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
        }
        return deleted;
    }
    public int pruneClosedBotTasks(int limit) {
        int deleted = this.memoryRepository.deleteClosedBotTasks(limit);
        if (deleted > 0) {
            this.memoryRepository.recordAction("bot-task-prune", "deleted=" + deleted + " limit=" + limit);
        }
        return deleted;
    }

    public boolean updateBotTaskObjective(long taskId, String objective) {
        boolean updated = this.memoryRepository.updateBotTaskObjective(taskId, objective);
        if (updated) {
            this.memoryRepository.recordAction("bot-task-update", "id=" + taskId + " objective=" + objective);
            this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
        }
        return updated;
    }
    public boolean markBotTaskDone(long taskId) {
        boolean updated = this.memoryRepository.updateBotTaskStatus(taskId, "DONE");
        if (updated) {
            this.memoryRepository.recordAction("bot-task-done-manual", "id=" + taskId);
            this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
        }
        return updated;
    }


    public boolean reopenBotTask(long taskId) {
        boolean updated = this.memoryRepository.updateBotTaskStatus(taskId, "PENDING");
        if (updated) {
            this.memoryRepository.recordAction("bot-task-reopen", "id=" + taskId);
            this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
        }
        return updated;
    }
    public boolean startBotTask(long taskId) {
        boolean updated = this.memoryRepository.updateBotTaskStatus(taskId, "ACTIVE");
        if (updated) {
            this.memoryRepository.recordAction("bot-task-start", "id=" + taskId);
            this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
        }
        return updated;
    }
    public boolean cancelBotTask(long taskId) {
        boolean updated = this.memoryRepository.updateBotTaskStatus(taskId, "CANCELED");
        if (updated) {
            this.memoryRepository.recordAction("bot-task-cancel", "id=" + taskId);
            this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
        }
        return updated;
    }

    public BotAskResult askBot(String playerId, String question) {
        String response = this.ollamaClient.ask(question, this.currentObjective)
            .orElse("(fallback) Objectif=" + this.currentObjective + ". Recoit ta question: " + question);
        long interactionId = this.memoryRepository.recordInteraction(playerId, question, response);
        if (interactionId > 0) {
            this.memoryRepository.recordAction("bot-ask", "id=" + interactionId + " player=" + playerId + " response=ok");
        }
        return new BotAskResult(interactionId, response);
    }

    public List<BotMemoryRepository.InteractionRecord> getRecentInteractions(int limit) {
        return this.memoryRepository.loadRecentInteractions(limit);
    }

    public DecisionStats getDecisionStats() {
        return new DecisionStats(decisionsMade, decisionSkips, lastDecisionAt);
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean pauseAI() {
        if (paused) {
            return false;
        }
        paused = true;
        this.memoryRepository.recordAction("bot-paused", "ok");
        return true;
    }

    public boolean resumeAI() {
        if (!paused) {
            return false;
        }
        paused = false;
        this.memoryRepository.recordAction("bot-resumed", "ok");
        return true;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean enabled) {
        this.debug = enabled;
        this.memoryRepository.recordAction("bot-debug", "enabled=" + enabled);
    }

    public BotGoal getActiveGoal() {
        return activeGoal;
    }

    public BotGoal setActiveGoal(String name, String description, String source) {
        BotGoal goal = memoryService.setActiveGoal(name, description, source);
        this.activeGoal = goal;
        this.memoryRepository.recordAction("bot-goal-set", "goal=" + goal.name() + " source=" + goal.source());
        return goal;
    }

    public List<BotGoal> listGoals(int limit) {
        int safeLimit = Math.max(1, Math.min(20, limit));
        return memoryService.loadGoals(safeLimit);
    }

    public boolean pauseActiveGoal() {
        boolean updated = memoryService.pauseActiveGoal();
        if (updated) {
            this.activeGoal = null;
        }
        return updated;
    }

    public boolean resumeActiveGoal() {
        boolean updated = memoryService.resumeLastPausedGoal();
        if (updated) {
            this.activeGoal = memoryService.loadActiveGoal().orElse(null);
        }
        return updated;
    }

    public String getCurrentPlanSummary() {
        BotActionPlan plan = actionExecutor.getCurrentPlan();
        if (plan == null || plan.isEmpty()) {
            return "none";
        }
        String rationale = plan.rationale();
        return "goal=" + plan.goal() + " steps=" + plan.steps().size() + (rationale.isBlank() ? "" : " rationale=" + rationale);
    }

    public String getCurrentStepSummary() {
        BotActionStep step = actionExecutor.getCurrentStep();
        if (step == null) {
            return "none";
        }
        String target = step.target() == null ? "none"
            : step.target().getX() + "," + step.target().getY() + "," + step.target().getZ();
        String item = (step.itemId() == null || step.itemId().isBlank()) ? "" : " item=" + step.itemId();
        return step.type().name() + " target=" + target + item + " count=" + step.count() + " timeout=" + step.timeoutTicks();
    }

    public String getLastPerceptionSummary() {
        return lastPerception == null ? "none" : lastPerception.summary();
    }

    public java.util.Optional<BotVitals> getBotVitals(ServerLevel level) {
        if (botFakePlayer != null) {
            return java.util.Optional.of(new BotVitals(
                botFakePlayer.getHealth(),
                botFakePlayer.getMaxHealth(),
                botFakePlayer.getFoodData().getFoodLevel()
            ));
        }
        AIBotEntity bot = getTrackedBot(level);
        if (bot == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new BotVitals(bot.getHealth(), bot.getMaxHealth(), botHunger));
    }

    public long getSurvivalMinutes() {
        return survivalMinutes;
    }

    public boolean isMineColoniesAvailable() {
        return this.mineColoniesBridge.isAvailable();
    }

    public Optional<MineColoniesBridge.ColonyInfo> getOwnedMineColoniesColony(ServerPlayer owner) {
        return this.mineColoniesBridge.getOwnedColony(owner);
    }

    public MineColoniesBridge.BridgeResult createMineColoniesColony(
        ServerPlayer owner,
        String colonyName,
        String styleName,
        int recruitCount
    ) {
        MineColoniesBridge.BridgeResult result = this.mineColoniesBridge.createOwnedColony(
            owner,
            owner.blockPosition(),
            colonyName,
            styleName,
            recruitCount
        );
        this.memoryRepository.recordAction("minecolonies-create", result.message());
        return result;
    }

    public MineColoniesBridge.BridgeResult claimMineColoniesColony(ServerPlayer owner) {
        MineColoniesBridge.BridgeResult result = this.mineColoniesBridge.claimNearestColony(owner, owner.blockPosition());
        this.memoryRepository.recordAction("minecolonies-claim", result.message());
        return result;
    }

    public MineColoniesBridge.BridgeResult recruitMineColoniesCitizens(ServerPlayer owner, int recruitCount) {
        MineColoniesBridge.BridgeResult result = this.mineColoniesBridge.recruitOwnedColony(owner, recruitCount);
        this.memoryRepository.recordAction("minecolonies-recruit", result.message());
        return result;
    }
    public Optional<BlockPos> getMineColoniesTownHall(ServerPlayer owner) {
        Optional<BlockPos> pos = this.mineColoniesBridge.getTownHallPosition(owner);
        this.memoryRepository.recordAction("minecolonies-townhall", pos.map(BlockPos::toShortString).orElse("none"));
        return pos;
    }

    public MineColoniesBridge.BridgeResult ensureMineColoniesCitizens(ServerPlayer owner, int targetCount) {
        MineColoniesBridge.BridgeResult result = this.mineColoniesBridge.ensureCitizenCount(owner, targetCount);
        this.memoryRepository.recordAction("minecolonies-ensure-citizens", result.message());
        return result;
    }


    public MineColoniesBridge.BridgeResult requestMineColoniesDeposit(ServerPlayer owner, String itemId, int quantity) {
        if (!isMineColoniesAvailable()) {
            return MineColoniesBridge.BridgeResult.failure("MineColonies n est pas charge");
        }

        Optional<MineColoniesBridge.ColonyInfo> colonyOptional = getOwnedMineColoniesColony(owner);
        if (colonyOptional.isEmpty()) {
            return MineColoniesBridge.BridgeResult.failure("Aucune colonie owner pour ce joueur");
        }

        int safeQuantity = Math.max(1, quantity);
        MineColoniesBridge.ColonyInfo colony = colonyOptional.get();
        String message = "Deposit request queued simule colonyId=" + colony.id()
            + " item=" + itemId
            + " qty=" + safeQuantity;
        this.memoryRepository.recordAction("minecolonies-request", message);
        return MineColoniesBridge.BridgeResult.success(message);
    }

    public MineColoniesBridge.BridgeResult enableMineColoniesMayorMode(ServerPlayer owner) {
        if (!isMineColoniesAvailable()) {
            return MineColoniesBridge.BridgeResult.failure("MineColonies n est pas charge");
        }

        Optional<MineColoniesBridge.ColonyInfo> colonyOptional = getOwnedMineColoniesColony(owner);
        if (colonyOptional.isEmpty()) {
            return MineColoniesBridge.BridgeResult.failure("Aucune colonie owner pour ce joueur");
        }

        MineColoniesBridge.ColonyInfo colony = colonyOptional.get();
        String message = "Mayor mode active simule colonyId=" + colony.id();
        this.memoryRepository.recordAction("minecolonies-mayor", message);
        return MineColoniesBridge.BridgeResult.success(message);
    }
    public boolean isAe2Available() {
        return this.ae2Bridge.isAvailable();
    }

    public AE2Bridge.AE2ScanResult scanAe2(ServerPlayer player, int radius) {
        AE2Bridge.AE2ScanResult result = this.ae2Bridge.scan(player.serverLevel(), player.blockPosition(), radius);
        this.memoryRepository.recordAction("ae2-scan", result.summary());
        return result;
    }

    public AE2Bridge.AE2ApiProbeResult probeAe2Api() {
        AE2Bridge.AE2ApiProbeResult result = this.ae2Bridge.probeApi();
        this.memoryRepository.recordAction("ae2-api-probe", result.summary());
        return result;
    }

    public long queueAe2CraftRequest(String itemId, int quantity, String requestedBy) {
        String key = itemId + ":" + quantity;
        Instant now = Instant.now();
        CachedRecipeRequest cached = recipeCache.get(key);
        if (cached != null && Duration.between(cached.cachedAt(), now).compareTo(recipeCacheTtl) < 0) {
            this.memoryRepository.recordAction("ae2-craft-cache", "item=" + itemId + " qty=" + quantity + " id=" + cached.requestId());
            return cached.requestId();
        }
        long requestId = this.memoryRepository.enqueueAe2CraftRequest(itemId, quantity, requestedBy);
        if (requestId > 0) {
            recipeCache.put(key, new CachedRecipeRequest(requestId, now));
            this.memoryRepository.recordAction(
                "ae2-craft-enqueue",
                "id=" + requestId + " item=" + itemId + " qty=" + quantity + " by=" + requestedBy
            );
        }
        return requestId;
    }

    public List<BotMemoryRepository.AE2CraftRequest> getPendingAe2CraftRequests(int limit) {
        return this.memoryRepository.loadPendingAe2CraftRequests(limit);
    }

    public int countPendingAe2CraftRequests() {
        return this.memoryRepository.countPendingAe2CraftRequests();
    }

    public List<BotMemoryRepository.AE2CraftRequest> getAe2CraftRequestHistory(int limit) {
        return this.memoryRepository.loadAe2CraftRequestHistory(limit);
    }

    public int countAe2CraftRequestsByStatus(String status) {
        return this.memoryRepository.countAe2CraftRequestsByStatus(status);
    }

    public List<BotMemoryRepository.AE2CraftRequest> getAe2CraftRequestHistoryByStatus(String status, int limit) {
        return this.memoryRepository.loadAe2CraftRequestHistoryByStatus(status, limit);
    }

    public int replayAe2CraftRequests(int limit) {
        int remaining = Math.max(1, Math.min(200, limit));
        int replayed = 0;
        List<String> statuses = List.of("DISPATCHED", "FAILED", "DONE", "CANCELED");
        for (String status : statuses) {
            if (remaining <= 0) {
                break;
            }
            int moved = this.memoryRepository.replayAe2CraftRequestsByStatus(status, remaining);
            replayed += moved;
            remaining -= moved;
        }
        if (replayed > 0) {
            this.memoryRepository.recordAction("ae2-craft-replay", "status=ALL replayed=" + replayed + " limit=" + limit);
        }
        return replayed;
    }

    public int replayAe2CraftRequestsByStatus(String status, int limit) {
        int replayed = this.memoryRepository.replayAe2CraftRequestsByStatus(status, limit);
        if (replayed > 0) {
            this.memoryRepository.recordAction("ae2-craft-replay", "status=" + status + " replayed=" + replayed + " limit=" + limit);
        }
        return replayed;
    }
    public boolean retryAe2CraftRequest(long requestId) {
        boolean retried = this.memoryRepository.retryAe2CraftRequest(requestId);
        if (retried) {
            this.memoryRepository.recordAction("ae2-craft-retry", "id=" + requestId);
        }
        return retried;
    }

    public boolean deleteAe2CraftRequest(long requestId) {
        boolean deleted = this.memoryRepository.deleteAe2CraftRequest(requestId);
        if (deleted) {
            this.memoryRepository.recordAction("ae2-craft-delete", "id=" + requestId);
        }
        return deleted;
    }

    public boolean failAe2CraftRequest(long requestId, String reason) {
        boolean failed = this.memoryRepository.updateAe2CraftRequestStatus(requestId, "FAILED", reason);
        if (failed) {
            this.memoryRepository.recordAction("ae2-craft-fail", "id=" + requestId + " reason=" + reason);
        }
        return failed;
    }
    public boolean cancelAe2CraftRequest(long requestId, String reason) {
        boolean canceled = this.memoryRepository.cancelAe2CraftRequest(requestId, reason);
        if (canceled) {
            this.memoryRepository.recordAction("ae2-craft-cancel", "id=" + requestId + " reason=" + reason);
        }
        return canceled;
    }
    public boolean doneAe2CraftRequest(long requestId, String message) {
        boolean done = this.memoryRepository.updateAe2CraftRequestStatus(requestId, "DONE", message);
        if (done) {
            this.memoryRepository.recordAction("ae2-craft-done", "id=" + requestId + " message=" + message);
        }
        return done;
    }

    public int clearNonPendingAe2CraftRequests(int limit) {
        int cleared = this.memoryRepository.clearNonPendingAe2CraftRequests(limit);
        if (cleared > 0) {
            this.memoryRepository.recordAction("ae2-craft-clear", "cleared=" + cleared + " limit=" + limit);
        }
        return cleared;
    }

    public int purgeClosedAe2CraftRequests(int limit) {
        int purged = this.memoryRepository.purgeClosedAe2CraftRequests(limit);
        if (purged > 0) {
            this.memoryRepository.recordAction("ae2-craft-purge", "purged=" + purged + " limit=" + limit);
        }
        return purged;
    }

    public boolean spawnMarker(ServerLevel level, Vec3 position) {
        return spawnMarker(level, position, "AIPlayer Bot");
    }

    public boolean spawnMarker(ServerLevel level, Vec3 position, String markerName) {
        ArmorStand marker = EntityType.ARMOR_STAND.create(level);
        if (marker == null) {
            return false;
        }

        marker.moveTo(position.x, position.y, position.z, 0.0f, 0.0f);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setCustomName(Component.literal(markerName));
        marker.setCustomNameVisible(true);
        marker.addTag(MARKER_TAG);

        boolean added = level.addFreshEntity(marker);
        if (added) {
            this.botMarkerEntityId = marker.getUUID();
            this.memoryRepository.recordAction("spawn-marker", this.botMarkerEntityId + " name=" + markerName);
        }

        return added;
    }

    public boolean spawnBot(ServerLevel level, Vec3 position, String botName) {
        AIBotEntity bot = AIBotEntities.AI_BOT.get().create(level);
        if (bot == null) {
            return false;
        }
        bot.moveTo(position.x, position.y, position.z, 0.0f, 0.0f);
        bot.setCustomName(Component.literal(botName));
        bot.setCustomNameVisible(true);
        bot.setPersistenceRequired();
        boolean added = level.addFreshEntity(bot);
        if (added) {
            this.botEntityId = bot.getUUID();
            this.botName = botName;
            this.botFakePlayer = null;
            this.currentPlan = null;
            this.actionExecutor.clearPlan();
            this.memoryRepository.recordAction("spawn-bot", this.botEntityId + " name=" + botName);
        }
        return added;
    }

    public boolean isBotAlive(ServerLevel level) {
        return getTrackedBot(level) != null;
    }

    public boolean despawnBot(ServerLevel level) {
        AIBotEntity bot = getTrackedBot(level);
        if (bot == null) {
            return false;
        }

        bot.discard();
        this.memoryRepository.recordAction("despawn-bot", bot.getUUID().toString());
        this.botEntityId = null;
        this.botFakePlayer = null;
        this.currentPlan = null;
        this.actionExecutor.clearPlan();
        return true;
    }

    public boolean despawnMarker(ServerLevel level) {
        Entity marker = getTrackedMarker(level);
        if (marker == null) {
            return false;
        }

        marker.discard();
        this.memoryRepository.recordAction("despawn-marker", marker.getUUID().toString());
        this.botMarkerEntityId = null;
        return true;
    }

    public boolean isMarkerAlive(ServerLevel level) {
        return getTrackedMarker(level) != null;
    }


    private void processSurvivalLoop(ServerLevel level) {
        AIBotEntity bot = getTrackedBot(level);
        if (bot == null) {
            return;
        }

        Instant now = Instant.now();
        if (botFakePlayer != null) {
            botHunger = botFakePlayer.getFoodData().getFoodLevel();
            bot.setHealth(botFakePlayer.getHealth());
            advanceSurvivalClock(now);
            return;
        }
        if (lastHungerAt == null || Duration.between(lastHungerAt, now).toSeconds() >= 60) {
            botHunger = Math.max(0, botHunger - 1);
            lastHungerAt = now;
            this.memoryRepository.recordAction("bot-hunger", "hunger=" + botHunger);
        }

        if (lastHarvestAt == null || Duration.between(lastHarvestAt, now).toSeconds() >= 120) {
            this.memoryRepository.recordAction("bot-harvest", "status=ok");
            lastHarvestAt = now;
            if (botHunger <= 10) {
                botHunger = 20;
                this.memoryRepository.recordAction("bot-eat", "hunger=" + botHunger);
            }
        }

        if (lastMineAt == null || Duration.between(lastMineAt, now).toSeconds() >= 180) {
            this.memoryRepository.recordAction("bot-mine", "status=ok");
            lastMineAt = now;
        }

        if (lastCraftAt == null || Duration.between(lastCraftAt, now).toSeconds() >= 240) {
            this.memoryRepository.recordAction("bot-craft", "status=ok");
            lastCraftAt = now;
        }

        if (level.isNight() && (lastSleepAt == null || Duration.between(lastSleepAt, now).toSeconds() >= 300)) {
            this.memoryRepository.recordAction("bot-sleep", "status=ok");
            lastSleepAt = now;
        }

        advanceSurvivalClock(now);

        if (botHunger == 0 && (lastHealAt == null || Duration.between(lastHealAt, now).toSeconds() >= 10)) {
            float nextHealth = Math.max(0.0f, bot.getHealth() - 1.0f);
            bot.setHealth(nextHealth);
            this.memoryRepository.recordAction("bot-starve", "health=" + nextHealth);
            lastHealAt = now;
        }

        if (bot.getHealth() < bot.getMaxHealth() && (lastHealAt == null || Duration.between(lastHealAt, now).toSeconds() >= 10)) {
            float nextHealth = Math.min(bot.getMaxHealth(), bot.getHealth() + 1.0f);
            bot.setHealth(nextHealth);
            this.memoryRepository.recordAction("bot-heal", "health=" + nextHealth);
            lastHealAt = now;
        }
    }

    private void advanceSurvivalClock(Instant now) {
        if (lastSurvivalAt == null) {
            lastSurvivalAt = now;
            return;
        }
        long seconds = Duration.between(lastSurvivalAt, now).getSeconds();
        if (seconds < 60) {
            return;
        }
        long minutes = seconds / 60;
        survivalMinutes += minutes;
        lastSurvivalAt = now;
        if (survivalMinutes >= 1440 && !survivalDayLogged) {
            survivalDayLogged = true;
            this.memoryRepository.recordAction("bot-survival-day", "minutes=" + survivalMinutes);
        }
    }

    private void processBotTaskLifecycle() {
        Optional<BotMemoryRepository.BotTask> currentOptional = this.memoryRepository.loadCurrentBotTask();
        if (currentOptional.isEmpty()) {
            this.currentObjective = "none";
            return;
        }

        BotMemoryRepository.BotTask task = currentOptional.get();
        this.currentObjective = task.objective();

        if ("PENDING".equals(task.status())) {
            boolean updated = this.memoryRepository.updateBotTaskStatus(task.id(), "ACTIVE");
            if (updated) {
                this.memoryRepository.recordAction("bot-task-active", "id=" + task.id() + " objective=" + task.objective());
            }
            return;
        }

        if ("ACTIVE".equals(task.status())) {
            boolean updated = this.memoryRepository.updateBotTaskStatus(task.id(), "DONE");
            if (updated) {
                this.memoryRepository.recordAction("bot-task-done", "id=" + task.id() + " objective=" + task.objective());
            }

            Optional<BotMemoryRepository.BotTask> nextOptional = this.memoryRepository.loadCurrentBotTask();
            if (nextOptional.isPresent()) {
                BotMemoryRepository.BotTask next = nextOptional.get();
                this.currentObjective = next.objective();
                if ("PENDING".equals(next.status())) {
                    boolean nextUpdated = this.memoryRepository.updateBotTaskStatus(next.id(), "ACTIVE");
                    if (nextUpdated) {
                        this.memoryRepository.recordAction("bot-task-active", "id=" + next.id() + " objective=" + next.objective());
                    }
                }
            } else {
                this.currentObjective = "none";
            }
        }
    }


    public int dispatchPendingAe2CraftRequests(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        int dispatched = 0;

        if (!isAe2Available()) {
            return 0;
        }

        for (BotMemoryRepository.AE2CraftRequest request : this.memoryRepository.loadPendingAe2CraftRequests(safeLimit)) {
            String resultMessage = "Dispatched to AE2 pipeline item=" + request.itemId() + " qty=" + request.quantity();
            boolean updated = this.memoryRepository.updateAe2CraftRequestStatus(request.id(), "DISPATCHED", resultMessage);
            if (updated) {
                this.memoryRepository.recordAction("ae2-craft-dispatch", "id=" + request.id() + " " + resultMessage);
                dispatched++;
            }
        }

        return dispatched;
    }

    private void processPendingAe2CraftRequests() {
        if (!isAe2Available()) {
            return;
        }

        dispatchPendingAe2CraftRequests(AE2_QUEUE_BATCH_SIZE);
    }

    public record BotAskResult(long interactionId, String response) {
    }

    public record DecisionStats(int decisionsMade, int decisionSkips, Instant lastDecisionAt) {
    }

    public record BotVitals(float health, float maxHealth, int hunger) {
    }

    private record CachedRecipeRequest(long requestId, Instant cachedAt) {
    }

    private boolean hasEnabledModulesEnvOverride() {
        String env = System.getenv("AIPLAYER_ENABLED_MODULES");
        return env != null && !env.isBlank();
    }

    private Duration resolvePathCacheTtl() {
        String env = System.getenv("AIPLAYER_PATH_CACHE_SECONDS");
        long seconds = 30;
        if (env != null && !env.isBlank()) {
            try {
                seconds = Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (seconds < 5) {
            seconds = 5;
        }
        if (seconds > 300) {
            seconds = 300;
        }
        return Duration.ofSeconds(seconds);
    }

    private Duration resolveRecipeCacheTtl() {
        String env = System.getenv("AIPLAYER_RECIPE_CACHE_SECONDS");
        long seconds = 120;
        if (env != null && !env.isBlank()) {
            try {
                seconds = Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (seconds < 30) {
            seconds = 30;
        }
        if (seconds > 600) {
            seconds = 600;
        }
        return Duration.ofSeconds(seconds);
    }

    private Duration resolveDecisionInterval() {
        String env = System.getenv("AIPLAYER_DECISION_INTERVAL_SECONDS");
        long seconds = 60;
        if (env != null && !env.isBlank()) {
            try {
                seconds = Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (seconds < 10) {
            seconds = 10;
        }
        if (seconds > 300) {
            seconds = 300;
        }
        return Duration.ofSeconds(seconds);
    }

    private double resolveMaxMspt() {
        String env = System.getenv("AIPLAYER_MAX_MSPT");
        double max = 50.0d;
        if (env != null && !env.isBlank()) {
            try {
                max = Double.parseDouble(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (max < 20.0d) {
            max = 20.0d;
        }
        if (max > 200.0d) {
            max = 200.0d;
        }
        return max;
    }

    private Duration resolveDegradedDecisionInterval() {
        String env = System.getenv("AIPLAYER_DECISION_DEGRADE_SECONDS");
        long seconds = 120;
        if (env != null && !env.isBlank()) {
            try {
                seconds = Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (seconds < 30) {
            seconds = 30;
        }
        if (seconds > 600) {
            seconds = 600;
        }
        return Duration.ofSeconds(seconds);
    }

    private int resolveDecisionIntervalTicks() {
        String prop = System.getProperty("aiplayer.decisionIntervalTicks");
        String env = System.getenv("AIPLAYER_DECISION_INTERVAL_TICKS");
        int ticks = 100;
        String raw = (prop != null && !prop.isBlank()) ? prop : env;
        if (raw != null && !raw.isBlank()) {
            try {
                ticks = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
            }
        } else {
            ticks = (int) Math.max(20, resolveDecisionInterval().getSeconds() * 20);
        }
        if (ticks < 20) {
            ticks = 20;
        }
        if (ticks > 1200) {
            ticks = 1200;
        }
        return ticks;
    }

    private int resolveActionTimeoutTicks() {
        String prop = System.getProperty("aiplayer.actionTimeoutTicks");
        String env = System.getenv("AIPLAYER_ACTION_TIMEOUT_TICKS");
        int ticks = 200;
        String raw = (prop != null && !prop.isBlank()) ? prop : env;
        if (raw != null && !raw.isBlank()) {
            try {
                ticks = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (ticks < 40) {
            ticks = 40;
        }
        if (ticks > 2400) {
            ticks = 2400;
        }
        return ticks;
    }

    private int resolveMaxPlanSteps() {
        String prop = System.getProperty("aiplayer.maxPlanSteps");
        String env = System.getenv("AIPLAYER_MAX_PLAN_STEPS");
        int steps = 8;
        String raw = (prop != null && !prop.isBlank()) ? prop : env;
        if (raw != null && !raw.isBlank()) {
            try {
                steps = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (steps < 1) {
            steps = 1;
        }
        if (steps > 25) {
            steps = 25;
        }
        return steps;
    }

    private boolean resolveDebug() {
        String prop = System.getProperty("aiplayer.debug");
        String env = System.getenv("AIPLAYER_DEBUG");
        String raw = (prop != null && !prop.isBlank()) ? prop : env;
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return raw.trim().equalsIgnoreCase("true") || raw.trim().equals("1") || raw.trim().equalsIgnoreCase("yes");
    }

    private String resolveOllamaUrl() {
        String prop = System.getProperty("aiplayer.ollama.url");
        String env = System.getenv("AIPLAYER_OLLAMA_URL");
        String raw = (prop != null && !prop.isBlank()) ? prop : env;
        return (raw == null || raw.isBlank()) ? "http://localhost:11434" : raw.trim();
    }

    private String resolveOllamaModel() {
        String prop = System.getProperty("aiplayer.ollama.model");
        String env = System.getenv("AIPLAYER_OLLAMA_MODEL");
        String raw = (prop != null && !prop.isBlank()) ? prop : env;
        return (raw == null || raw.isBlank()) ? "llama3.1:8b" : raw.trim();
    }


    private AIBotEntity getTrackedBot(ServerLevel level) {
        if (this.botEntityId == null) {
            return null;
        }

        Entity entity = level.getEntity(this.botEntityId);
        if (entity instanceof AIBotEntity bot) {
            return bot;
        }

        return null;
    }

    private Entity getTrackedMarker(ServerLevel level) {
        if (this.botMarkerEntityId == null) {
            return null;
        }

        Entity entity = level.getEntity(this.botMarkerEntityId);
        if (entity != null && entity.getTags().contains(MARKER_TAG)) {
            return entity;
        }

        return null;
    }
}
























