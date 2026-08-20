package com.bettercontent.latentchemlib.client;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import com.bettercontent.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.bettercontent.latentchemlib.sim.ChemicalCloudVisuals;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LatentChemlibMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ChemicalCloudClient {
    private ChemicalCloudClient() {}

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0 || level == null || pos == null) return 0xFFFFFF;
            if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) {
                return ChemicalCloudVisuals.FALLBACK_COLOR;
            }
            return ChemicalCloudVisuals.colorForPollutant(cloud.pollutantState().pollutantId());
        }, LatentChemlibMod.CHEMICAL_CLOUD.get());
    }
}
