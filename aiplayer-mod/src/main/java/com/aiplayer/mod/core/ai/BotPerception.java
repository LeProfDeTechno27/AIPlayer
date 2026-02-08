package com.aiplayer.mod.core.ai;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.StringJoiner;

public record BotPerception(
    String phase,
    String objective,
    float health,
    float maxHealth,
    int hunger,
    int xp,
    BlockPos position,
    String dimension,
    String biome,
    long timeOfDay,
    long day,
    List<String> nearbyEntities,
    List<String> nearbyBlocks,
    List<String> inventorySummary,
    List<String> equipmentSummary
) {
    public BotPerception {
        if (phase == null) {
            phase = "unknown";
        }
        if (objective == null) {
            objective = "none";
        }
        if (dimension == null) {
            dimension = "unknown";
        }
        if (biome == null) {
            biome = "unknown";
        }
        if (nearbyEntities == null) {
            nearbyEntities = List.of();
        }
        if (nearbyBlocks == null) {
            nearbyBlocks = List.of();
        }
        if (inventorySummary == null) {
            inventorySummary = List.of();
        }
        if (equipmentSummary == null) {
            equipmentSummary = List.of();
        }
    }

    public String summary() {
        StringJoiner joiner = new StringJoiner(" | ");
        joiner.add("phase=" + phase);
        joiner.add("objective=" + objective);
        joiner.add(String.format("health=%.1f/%.1f hunger=%d xp=%d", health, maxHealth, hunger, xp));
        if (position != null) {
            joiner.add("pos=" + position.getX() + "," + position.getY() + "," + position.getZ());
        }
        joiner.add("dimension=" + dimension);
        joiner.add("biome=" + biome);
        joiner.add("time=" + timeOfDay + " day=" + day);
        if (!nearbyEntities.isEmpty()) {
            joiner.add("entities=" + String.join(",", nearbyEntities));
        }
        if (!nearbyBlocks.isEmpty()) {
            joiner.add("blocks=" + String.join(",", nearbyBlocks));
        }
        if (!inventorySummary.isEmpty()) {
            joiner.add("inventory=" + String.join(",", inventorySummary));
        }
        if (!equipmentSummary.isEmpty()) {
            joiner.add("equipment=" + String.join(",", equipmentSummary));
        }
        return joiner.toString();
    }
}