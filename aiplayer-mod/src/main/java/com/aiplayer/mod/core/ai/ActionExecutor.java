package com.aiplayer.mod.core.ai;

import com.aiplayer.mod.entity.AIBotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;

public final class ActionExecutor {
    private final FakePlayerController fakePlayerController;
    private final BotMemoryService memoryService;

    private BotActionPlan currentPlan;
    private int stepIndex;
    private int ticksOnStep;
    private ActionResult lastResult;

    public ActionExecutor(FakePlayerController fakePlayerController, BotMemoryService memoryService) {
        this.fakePlayerController = fakePlayerController;
        this.memoryService = memoryService;
    }

    public void setPlan(BotActionPlan plan) {
        if (plan == null) {
            clearPlan();
            return;
        }
        this.currentPlan = plan;
        this.stepIndex = 0;
        this.ticksOnStep = 0;
        this.lastResult = null;
    }

    public void clearPlan() {
        this.currentPlan = null;
        this.stepIndex = 0;
        this.ticksOnStep = 0;
        this.lastResult = null;
    }

    public BotActionPlan getCurrentPlan() {
        return currentPlan;
    }

    public boolean isIdle() {
        return currentPlan == null || currentPlan.steps().isEmpty() || stepIndex >= currentPlan.steps().size();
    }

    public BotActionStep getCurrentStep() {
        if (currentPlan == null || currentPlan.steps().isEmpty() || stepIndex >= currentPlan.steps().size()) {
            return null;
        }
        return currentPlan.steps().get(stepIndex);
    }

    public ActionResult getLastResult() {
        return lastResult;
    }

    public void tick(ServerLevel level, AIBotEntity bot, String botName) {
        if (isIdle()) {
            return;
        }
        BotActionStep step = currentPlan.steps().get(stepIndex);
        ticksOnStep++;

        FakePlayer player = fakePlayerController.getOrCreate(level, botName);
        if (bot != null && player != null) {
            fakePlayerController.syncPosition(player, bot.position());
        }

        if (step.type() == BotActionType.WAIT) {
            if (ticksOnStep >= step.timeoutTicks()) {
                finalizeStep(step, ActionResult.success("wait complete"));
            }
            return;
        }

        if (ticksOnStep > step.timeoutTicks()) {
            finalizeStep(step, ActionResult.failure("timeout"));
            return;
        }

        ActionResult result = executeStep(level, bot, botName, step);
        if (result.completed()) {
            finalizeStep(step, result);
        }
    }

    private void finalizeStep(BotActionStep step, ActionResult result) {
        lastResult = result;
        memoryService.recordActionHistory(currentPlan, step, result, stepIndex);
        stepIndex++;
        ticksOnStep = 0;
    }

    private ActionResult executeStep(ServerLevel level, AIBotEntity bot, String botName, BotActionStep step) {
        return switch (step.type()) {
            case MOVE -> doMove(bot, step);
            case MINE -> doMine(level, botName, bot, step);
            case PLACE -> doPlace(level, bot, step);
            case ATTACK -> doAttack(level, botName, bot, step);
            case CRAFT -> doCraft(level, botName, step);
            case INTERACT -> doInteract(level, botName, step);
            case EQUIP -> doEquip(level, botName, step);
            case WAIT -> ActionResult.running("waiting");
        };
    }

    private ActionResult doMove(AIBotEntity bot, BotActionStep step) {
        if (bot == null || step.target() == null) {
            return ActionResult.failure("no target");
        }
        Vec3 target = new Vec3(step.target().getX() + 0.5, step.target().getY(), step.target().getZ() + 0.5);
        double distance = bot.position().distanceToSqr(target);
        if (distance <= 2.25) {
            return ActionResult.success("arrived");
        }
        bot.getNavigation().moveTo(target.x, target.y, target.z, 1.1);
        return ActionResult.running("moving");
    }

    private ActionResult doMine(ServerLevel level, String botName, AIBotEntity bot, BotActionStep step) {
        if (step.target() == null) {
            return ActionResult.failure("missing target");
        }
        if (bot != null) {
            Vec3 target = Vec3.atCenterOf(step.target());
            double distance = bot.position().distanceToSqr(target);
            if (distance > 9.0) {
                bot.getNavigation().moveTo(target.x, target.y, target.z, 1.1);
                return ActionResult.running("moving to target");
            }
        }
        BlockState state = level.getBlockState(step.target());
        if (state.isAir()) {
            return ActionResult.success("already mined");
        }
        FakePlayer player = fakePlayerController.getOrCreate(level, botName);
        boolean destroyed = fakePlayerController.destroyBlock(player, step.target());
        if (destroyed || level.getBlockState(step.target()).isAir()) {
            return ActionResult.success("block mined");
        }
        return ActionResult.failure("failed to mine");
    }

    private ActionResult doPlace(ServerLevel level, AIBotEntity bot, BotActionStep step) {
        if (step.target() == null || step.itemId() == null || step.itemId().isBlank()) {
            return ActionResult.failure("missing target/item");
        }
        if (bot != null) {
            Vec3 target = Vec3.atCenterOf(step.target());
            double distance = bot.position().distanceToSqr(target);
            if (distance > 9.0) {
                bot.getNavigation().moveTo(target.x, target.y, target.z, 1.1);
                return ActionResult.running("moving to target");
            }
        }
        if (!level.getBlockState(step.target()).isAir()) {
            return ActionResult.success("target occupied");
        }
        Block block = resolveBlock(step.itemId());
        if (block == null) {
            return ActionResult.failure("unknown block");
        }
        boolean placed = fakePlayerController.placeBlock(level, step.target(), block.defaultBlockState());
        return placed ? ActionResult.success("block placed") : ActionResult.failure("place failed");
    }

    private ActionResult doAttack(ServerLevel level, String botName, AIBotEntity bot, BotActionStep step) {
        if (bot == null) {
            return ActionResult.failure("no bot");
        }
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, new AABB(bot.blockPosition()).inflate(6.0));
        if (monsters.isEmpty()) {
            return ActionResult.failure("no target");
        }
        Entity target = monsters.get(0);
        FakePlayer player = fakePlayerController.getOrCreate(level, botName);
        boolean hit = fakePlayerController.attack(player, target);
        return hit ? ActionResult.success("attack") : ActionResult.failure("attack failed");
    }

    private ActionResult doCraft(ServerLevel level, String botName, BotActionStep step) {
        if (step.itemId() == null || step.itemId().isBlank()) {
            return ActionResult.failure("missing item");
        }
        Item item = resolveItem(step.itemId());
        if (item == null) {
            return ActionResult.failure("unknown item");
        }
        FakePlayer player = fakePlayerController.getOrCreate(level, botName);
        ItemStack stack = new ItemStack(item, step.count());
        boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false);
        }
        return ActionResult.success("crafted " + step.itemId());
    }

    private ActionResult doEquip(ServerLevel level, String botName, BotActionStep step) {
        if (step.itemId() == null || step.itemId().isBlank()) {
            return ActionResult.failure("missing item");
        }
        FakePlayer player = fakePlayerController.getOrCreate(level, botName);
        int slot = findInventorySlot(player, step.itemId());
        if (slot < 0) {
            return ActionResult.failure("item not found");
        }
        player.getInventory().selected = slot;
        return ActionResult.success("equipped " + step.itemId());
    }

    private ActionResult doInteract(ServerLevel level, String botName, BotActionStep step) {
        if (step.target() == null) {
            return ActionResult.failure("missing target");
        }
        BlockState state = level.getBlockState(step.target());
        if (state.isAir()) {
            return ActionResult.failure("no block");
        }
        FakePlayer player = fakePlayerController.getOrCreate(level, botName);
        Vec3 hitPos = Vec3.atCenterOf(step.target());
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, step.target(), false);
        InteractionResult result = player.gameMode.useItemOn(
            player,
            level,
            player.getMainHandItem(),
            InteractionHand.MAIN_HAND,
            hit
        );
        if (result.consumesAction()) {
            return ActionResult.success("interacted");
        }
        return ActionResult.failure("interact failed");
    }

    private Block resolveBlock(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null) {
            return null;
        }
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.get(key);
    }

    private Item resolveItem(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null) {
            return null;
        }
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(key);
    }

    private int findInventorySlot(FakePlayer player, String itemId) {
        if (player == null) {
            return -1;
        }
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null && key.toString().equals(itemId)) {
                return i;
            }
        }
        return -1;
    }
}
