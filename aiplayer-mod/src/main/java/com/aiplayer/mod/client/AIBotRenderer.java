package com.aiplayer.mod.client;

import com.aiplayer.mod.entity.AIBotEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class AIBotRenderer extends HumanoidMobRenderer<AIBotEntity, PlayerModel<AIBotEntity>> {
    private static final ResourceLocation STEVE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    public AIBotRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(AIBotEntity entity) {
        return STEVE_TEXTURE;
    }
}
