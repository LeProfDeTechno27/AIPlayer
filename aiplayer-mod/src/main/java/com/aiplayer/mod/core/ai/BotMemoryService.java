package com.aiplayer.mod.core.ai;

import com.aiplayer.mod.persistence.BotMemoryRepository;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

public final class BotMemoryService {
    private final BotMemoryRepository repository;

    public BotMemoryService(BotMemoryRepository repository) {
        this.repository = repository;
    }

    public BotGoal setActiveGoal(String name, String description, String source) {
        BotMemoryRepository.BotGoalRecord record = repository.setActiveGoal(name, description, source);
        return toGoal(record);
    }

    public Optional<BotGoal> loadActiveGoal() {
        return repository.loadActiveGoal().map(this::toGoal);
    }

    public List<BotGoal> loadGoals(int limit) {
        return repository.loadGoals(limit).stream().map(this::toGoal).toList();
    }

    public boolean pauseActiveGoal() {
        return repository.pauseActiveGoal();
    }

    public boolean resumeLastPausedGoal() {
        return repository.resumeLastPausedGoal();
    }

    public void recordActionHistory(BotActionPlan plan, BotActionStep step, ActionResult result, int stepIndex) {
        repository.recordActionHistory(
            plan.goal(),
            stepIndex,
            step.type().name(),
            formatTarget(step),
            step.itemId(),
            step.count(),
            result.success(),
            result.message()
        );
    }

    public void recordInventorySnapshot(List<String> summary) {
        repository.recordInventorySnapshot(String.join(",", summary));
    }

    public void recordKnownLocation(String label, BlockPos pos, String dimension) {
        if (pos == null) {
            return;
        }
        repository.recordKnownLocation(label, pos.getX(), pos.getY(), pos.getZ(), dimension);
    }

    public Optional<String> loadLatestInventorySummary() {
        return repository.loadLatestInventorySnapshot().map(BotMemoryRepository.InventorySnapshotRecord::summary);
    }

    public List<BotMemoryRepository.KnownLocationRecord> loadKnownLocations(int limit) {
        return repository.loadKnownLocations(limit);
    }

    public List<BotMemoryRepository.ActionHistoryRecord> loadRecentActionHistory(int limit) {
        return repository.loadRecentActionHistory(limit);
    }

    private String formatTarget(BotActionStep step) {
        if (step.target() == null) {
            return "";
        }
        return step.target().getX() + "," + step.target().getY() + "," + step.target().getZ();
    }

    private BotGoal toGoal(BotMemoryRepository.BotGoalRecord record) {
        return new BotGoal(
            record.goal(),
            record.description(),
            record.source(),
            record.status(),
            record.createdAt()
        );
    }
}
