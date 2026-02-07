package com.aiplayer.mod.entity;

import com.aiplayer.mod.ModMetadata;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class AIBotEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, ModMetadata.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<AIBotEntity>> AI_BOT = ENTITIES.register(
        "ai_bot",
        () -> EntityType.Builder.of(AIBotEntity::new, MobCategory.MISC)
            .sized(0.6f, 1.8f)
            .build("ai_bot")
    );

    private AIBotEntities() {
    }
}
