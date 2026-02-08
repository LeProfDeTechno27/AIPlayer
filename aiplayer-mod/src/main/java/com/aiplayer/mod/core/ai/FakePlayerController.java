package com.aiplayer.mod.core.ai;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class FakePlayerController {
    public FakePlayer getOrCreate(ServerLevel level, String name) {
        GameProfile profile = new GameProfile(stableUuid(name), name);
        FakePlayer player = FakePlayerFactory.get(level, profile);
        applyAbilities(player);
        return player;
    }

    public void syncPosition(FakePlayer player, Vec3 pos) {
        player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
    }

    public boolean destroyBlock(FakePlayer player, BlockPos pos) {
        return player.serverLevel().destroyBlock(pos, true, player);
    }

    public boolean placeBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state == null) {
            return false;
        }
        return level.setBlockAndUpdate(pos, state);
    }

    public boolean attack(FakePlayer player, Entity target) {
        if (target == null) {
            return false;
        }
        return player.doHurtTarget(target);
    }

    private UUID stableUuid(String name) {
        return UUID.nameUUIDFromBytes(("aiplayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private void applyAbilities(FakePlayer player) {
        player.getAbilities().instabuild = true;
        player.getAbilities().invulnerable = true;
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }
}