package com.slizerozik.demoncow.client;

import com.slizerozik.demoncow.DemonCowMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = DemonCowMod.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DemonCowModel.LAYER, DemonCowModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DemonCowMod.DEMON_COW.get(), DemonCowRenderer::new);
        event.registerEntityRenderer(DemonCowMod.SUMMON_PORTAL.get(), SummonPortalRenderer::new);
    }
}
