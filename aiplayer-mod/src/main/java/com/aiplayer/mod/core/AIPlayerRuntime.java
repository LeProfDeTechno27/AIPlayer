package com.aiplayer.mod.core;

import com.aiplayer.mod.entity.AIBotEntities;
import com.aiplayer.mod.entity.AIBotEntity;
import com.aiplayer.mod.integrations.AE2Bridge;
import com.aiplayer.mod.integrations.MineColoniesBridge;
import com.aiplayer.mod.integrations.OllamaClient;
import com.aiplayer.mod.persistence.BotMemoryRepository;
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
    private final BotBrain botBrain;

    private String phase;
    private UUID botMarkerEntityId;
    private UUID botEntityId;
    private int botHunger = 20;
    private Instant lastHungerAt;
    private Instant lastHealAt;
    private Instant lastHarvestAt;
    private Instant lastMineAt;
    private Instant lastCraftAt;
    private Instant lastSleepAt;
    private Instant lastSurvivalAt;
    private Instant lastDecisionStatsAt;
    private int lastDecisionStatsTotal;
    private int lastDecisionStatsRateLimit;
    private int lastDecisionStatsCache;
    private Instant lastTickLogAt;
    private Instant lastDegradedDecisionAt;
    private Instant lastDegradeLogAt;
    private final Duration degradedDecisionInterval = resolveDegradedDecisionInterval();
    private final double maxMspt = resolveMaxMspt();
    private Instant lastMsptLogAt;
    private final Duration pathCacheTtl = resolvePathCacheTtl();
    private final Duration recipeCacheTtl = resolveRecipeCacheTtl();
    private Vec3 lastMoveTarget;
    private Instant lastMoveTargetAt;
    private final Map<String, CachedRecipeRequest> recipeCache = new HashMap<>();
    private long survivalMinutes;
    private boolean survivalDayLogged;
    private String currentObjective;
    private int botXp;

    public AIPlayerRuntime(ModuleManager moduleManager, BotMemoryRepository memoryRepository) {
        this.moduleManager = moduleManager;
        this.memoryRepository = memoryRepository;
        this.mineColoniesBridge = new MineColoniesBridge();
        this.ae2Bridge = new AE2Bridge();
        this.ollamaClient = new OllamaClient(resolveOllamaUrl(), resolveOllamaModel());
        this.botBrain = new BotBrain(this.ollamaClient, resolveDecisionInterval());
        this.phase = this.memoryRepository.loadCurrentPhase().orElse("bootstrap");
        this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
        this.botXp = this.memoryRepository.loadBotXp().orElse(0);
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
        boolean allowBrain = !shouldThrottleForMspt(level, now);
        int bufferSize = this.memoryRepository.getActionBufferSize();
        int bufferWarn = this.memoryRepository.getActionBufferWarnThreshold();
        int degradeThreshold = Math.max(1, bufferWarn / 2);
        if (!allowBrain) {
            lastDegradedDecisionAt = null;
        } else if (bufferSize >= bufferWarn) {
            this.memoryRepository.recordAction("bot-brain-throttle", "action-buffer size=" + bufferSize);
        } else if (bufferSize >= degradeThreshold) {
            if (shouldAllowDegradedDecision(now)) {
                processBotBrainDecision(level);
                lastDegradedDecisionAt = now;
            } else {
                logDegradedSkip(now, bufferSize, bufferWarn);
            }
        } else {
            processBotBrainDecision(level);
            lastDegradedDecisionAt = null;
        }
        processSurvivalLoop(level);
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

    private void recordDecisionStats(Instant now) {
        if (lastDecisionStatsAt != null && Duration.between(lastDecisionStatsAt, now).compareTo(Duration.ofMinutes(5)) < 0) {
            return;
        }
        BotBrain.DecisionStats stats = this.botBrain.getDecisionStats();
        int deltaDecisions = stats.totalDecisions() - lastDecisionStatsTotal;
        int deltaRateLimit = stats.rateLimitSkips() - lastDecisionStatsRateLimit;
        int deltaCache = stats.cacheHits() - lastDecisionStatsCache;
        if (deltaDecisions == 0 && deltaRateLimit == 0 && deltaCache == 0) {
            lastDecisionStatsAt = now;
            return;
        }
        this.memoryRepository.recordAction(
            "bot-brain-stats",
            "decisions=" + stats.totalDecisions()
                + " cache=" + stats.cacheHits()
                + " rateLimit=" + stats.rateLimitSkips()
        );
        if (deltaRateLimit > 0) {
            this.memoryRepository.recordAction(
                "bot-brain-rate-limit",
                "skipped=" + deltaRateLimit
                    + " total=" + stats.rateLimitSkips()
                    + " decisions=" + stats.totalDecisions()
                    + " cache=" + stats.cacheHits()
            );
        }
        lastDecisionStatsAt = now;
        lastDecisionStatsTotal = stats.totalDecisions();
        lastDecisionStatsRateLimit = stats.rateLimitSkips();
        lastDecisionStatsCache = stats.cacheHits();
    }

    private boolean shouldAllowDegradedDecision(Instant now) {
        if (lastDegradedDecisionAt == null) {
            return true;
        }
        return Duration.between(lastDegradedDecisionAt, now).compareTo(degradedDecisionInterval) >= 0;
    }

    private void logDegradedSkip(Instant now, int bufferSize, int bufferWarn) {
        if (lastDegradeLogAt != null && Duration.between(lastDegradeLogAt, now).compareTo(Duration.ofMinutes(1)) < 0) {
            return;
        }
        this.memoryRepository.recordAction(
            "bot-brain-degraded",
            "action-buffer size=" + bufferSize + " warn=" + bufferWarn
        );
        lastDegradeLogAt = now;
    }

    private void recordTickLog(Instant now) {
        if (lastTickLogAt != null && Duration.between(lastTickLogAt, now).compareTo(Duration.ofMinutes(1)) < 0) {
            return;
        }
        this.memoryRepository.recordAction("tick", "ok");
        lastTickLogAt = now;
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

    public java.util.Optional<BotBrain.BotDecision> getLastDecision() {
        return this.botBrain.getLastDecision();
    }

    public BotBrain.DecisionStats getDecisionStats() {
        return this.botBrain.getDecisionStats();
    }
    public java.util.Optional<BotVitals> getBotVitals(ServerLevel level) {
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
        boolean added = level.addFreshEntity(bot);
        if (added) {
            this.botEntityId = bot.getUUID();
            this.memoryRepository.recordAction("spawn-bot", this.botEntityId + " name=" + botName);
        }
        return added;
    }

    public boolean isBotAlive(ServerLevel level) {
        return getTrackedBot(level) != null;
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

    private void executeDecision(ServerLevel level, BotBrain.BotDecision decision) {
        AIBotEntity bot = getTrackedBot(level);
        if (bot == null) {
            return;
        }

        switch (decision.action()) {
            case MOVE -> {
                Vec3 target = resolveMoveTarget(bot, Instant.now());
                bot.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
                this.memoryRepository.recordAction("bot-brain-action", "move target=" + target.x + "," + target.y + "," + target.z);
            }
            case INTERACT -> {
                var player = level.getNearestPlayer(bot, 6.0);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("AIPlayer: besoin d'aide pour " + this.currentObjective));
                    this.memoryRepository.recordAction("bot-brain-action", "interact player=" + player.getGameProfile().getName());
                }
            }
            case SLEEP -> this.memoryRepository.recordAction("bot-brain-action", "sleep requested");
            case MINE -> this.memoryRepository.recordAction("bot-brain-action", "mine requested");
            case CRAFT -> this.memoryRepository.recordAction("bot-brain-action", "craft requested");
            case BUILD -> this.memoryRepository.recordAction("bot-brain-action", "build requested");
            case IDLE -> this.memoryRepository.recordAction("bot-brain-action", "idle");
        }
    }

    private Vec3 resolveMoveTarget(AIBotEntity bot, Instant now) {
        if (lastMoveTarget != null && lastMoveTargetAt != null
            && Duration.between(lastMoveTargetAt, now).compareTo(pathCacheTtl) < 0) {
            return lastMoveTarget;
        }
        double dx = bot.getX() + (bot.getRandom().nextDouble() * 8.0 - 4.0);
        double dz = bot.getZ() + (bot.getRandom().nextDouble() * 8.0 - 4.0);
        double dy = bot.getY();
        lastMoveTarget = new Vec3(dx, dy, dz);
        lastMoveTargetAt = now;
        return lastMoveTarget;
    }

    private void processSurvivalLoop(ServerLevel level) {
        AIBotEntity bot = getTrackedBot(level);
        if (bot == null) {
            return;
        }

        Instant now = Instant.now();
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
    private String resolveOllamaUrl() {
        String env = System.getenv("AIPLAYER_OLLAMA_URL");
        return (env == null || env.isBlank()) ? "http://localhost:11434" : env.trim();
    }

    private String resolveOllamaModel() {
        String env = System.getenv("AIPLAYER_OLLAMA_MODEL");
        return (env == null || env.isBlank()) ? "qwen3:8b" : env.trim();
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






















