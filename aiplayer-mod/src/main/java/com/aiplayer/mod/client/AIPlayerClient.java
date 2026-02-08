package com.aiplayer.mod.client;

import com.aiplayer.mod.ModMetadata;
import com.aiplayer.mod.entity.AIBotEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod.EventBusSubscriber(modid = ModMetadata.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AIPlayerClient {
    private AIPlayerClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AIBotEntities.AI_BOT.get(), AIBotRenderer::new);
    }
}