package com.aiplayer.mod.core;

import com.aiplayer.mod.integrations.AE2Bridge;
import com.aiplayer.mod.integrations.MineColoniesBridge;
import com.aiplayer.mod.persistence.BotMemoryRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AIPlayerRuntime {
    private static final String MARKER_TAG = "aiplayer_bot_marker";
    private static final int AE2_QUEUE_BATCH_SIZE = 1;

    private final ModuleManager moduleManager;
    private final BotMemoryRepository memoryRepository;
    private final MineColoniesBridge mineColoniesBridge;
    private final AE2Bridge ae2Bridge;

    private String phase;
    private UUID botMarkerEntityId;
    private String currentObjective;

    public AIPlayerRuntime(ModuleManager moduleManager, BotMemoryRepository memoryRepository) {
        this.moduleManager = moduleManager;
        this.memoryRepository = memoryRepository;
        this.mineColoniesBridge = new MineColoniesBridge();
        this.ae2Bridge = new AE2Bridge();
        this.phase = this.memoryRepository.loadCurrentPhase().orElse("bootstrap");
        this.currentObjective = this.memoryRepository.loadCurrentBotTask().map(BotMemoryRepository.BotTask::objective).orElse("none");
    }

    public void initialize() {
        this.moduleManager.initialize(new BotContext(this.phase));
    }

    public String getPhase() {
        return this.phase;
    }

    public String getCurrentObjective() {
        return this.currentObjective;
    }

    public void setPhase(String nextPhase) {
        this.phase = nextPhase;
        this.memoryRepository.saveCurrentPhase(nextPhase);
        this.memoryRepository.recordAction("set-phase", nextPhase);
    }

    public void tickOnce() {
        this.moduleManager.tickEnabled();
        refreshCurrentObjective();
        processPendingAe2CraftRequests();
        this.memoryRepository.recordAction("tick", "ok");
    }

    public boolean enableModule(String moduleName) {
        boolean enabled = this.moduleManager.enableModule(moduleName);
        if (enabled) {
            this.memoryRepository.recordAction("enable-module", moduleName);
        }
        return enabled;
    }

    public boolean disableModule(String moduleName) {
        boolean disabled = this.moduleManager.disableModule(moduleName);
        if (disabled) {
            this.memoryRepository.recordAction("disable-module", moduleName);
        }
        return disabled;
    }

    public List<String> getEnabledModules() {
        return this.moduleManager.getEnabledModuleNames();
    }

    public List<String> getRegisteredModules() {
        return this.moduleManager.getRegisteredModuleNames();
    }

    public long queueBotTask(String objective, String requestedBy) {
        long taskId = this.memoryRepository.enqueueBotTask(objective, requestedBy);
        if (taskId > 0) {
            this.memoryRepository.recordAction("bot-task-enqueue", "id=" + taskId + " by=" + requestedBy + " objective=" + objective);
            if ("none".equals(this.currentObjective)) {
                this.currentObjective = objective;
                this.memoryRepository.updateBotTaskStatus(taskId, "ACTIVE");
            }
        }
        return taskId;
    }

    public List<BotMemoryRepository.BotTask> getOpenBotTasks(int limit) {
        return this.memoryRepository.loadOpenBotTasks(limit);
    }

    public int countOpenBotTasks() {
        return this.memoryRepository.countOpenBotTasks();
    }
    public long askBot(String playerId, String question) {
        String response = "Question recue: " + question + " | objectif courant=" + this.currentObjective;
        long interactionId = this.memoryRepository.recordInteraction(playerId, question, response);
        if (interactionId > 0) {
            this.memoryRepository.recordAction("bot-ask", "id=" + interactionId + " player=" + playerId);
        }
        return interactionId;
    }

    public List<BotMemoryRepository.InteractionRecord> getRecentInteractions(int limit) {
        return this.memoryRepository.loadRecentInteractions(limit);
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
        long requestId = this.memoryRepository.enqueueAe2CraftRequest(itemId, quantity, requestedBy);
        if (requestId > 0) {
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

    private void refreshCurrentObjective() {
        Optional<BotMemoryRepository.BotTask> current = this.memoryRepository.loadCurrentBotTask();
        if (current.isPresent()) {
            BotMemoryRepository.BotTask task = current.get();
            this.currentObjective = task.objective();
            if ("PENDING".equals(task.status())) {
                this.memoryRepository.updateBotTaskStatus(task.id(), "ACTIVE");
            }
        } else {
            this.currentObjective = "none";
        }
    }

    private void processPendingAe2CraftRequests() {
        if (!isAe2Available()) {
            return;
        }

        for (BotMemoryRepository.AE2CraftRequest request : this.memoryRepository.loadPendingAe2CraftRequests(AE2_QUEUE_BATCH_SIZE)) {
            String resultMessage = "Dispatched to AE2 pipeline item=" + request.itemId() + " qty=" + request.quantity();
            boolean updated = this.memoryRepository.updateAe2CraftRequestStatus(request.id(), "DISPATCHED", resultMessage);
            if (updated) {
                this.memoryRepository.recordAction("ae2-craft-dispatch", "id=" + request.id() + " " + resultMessage);
            }
        }
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