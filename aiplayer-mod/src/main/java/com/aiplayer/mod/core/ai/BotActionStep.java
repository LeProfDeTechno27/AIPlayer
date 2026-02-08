package com.aiplayer.mod.core.ai;

import net.minecraft.core.BlockPos;

public record BotActionStep(
    BotActionType type,
    BlockPos target,
    String itemId,
    int count,
    int timeoutTicks
) {
    public BotActionStep {
        if (type == null) {
            type = BotActionType.WAIT;
        }
        if (count <= 0) {
            count = 1;
        }
        if (timeoutTicks <= 0) {
            timeoutTicks = 200;
        }
    }

    public boolean hasTarget() {
        return target != null;
    }
}