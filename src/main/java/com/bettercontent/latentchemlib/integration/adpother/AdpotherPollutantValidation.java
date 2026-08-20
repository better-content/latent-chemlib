package com.bettercontent.latentchemlib.integration.adpother;

import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;

/** Refuses to start with a partial atmospheric vocabulary. */
public final class AdpotherPollutantValidation {
    public static final AdpotherPollutantValidation INSTANCE = new AdpotherPollutantValidation();
    private AdpotherPollutantValidation() {}

    @SubscribeEvent
    public void validate(ServerAboutToStartEvent event) {
        List<String> missing = new ArrayList<>();
        ForgeRegistries.ITEMS.getEntries().forEach(entry -> {
            if (entry.getValue() instanceof Chemical chemical && chemical.getMatterState() == MatterState.GAS
                && AdpotherGasBoundary.INSTANCE.pollutantFor(entry.getKey().location().toString()).isEmpty()) {
                missing.add(entry.getKey().location().toString());
            }
        });
        if (!missing.isEmpty()) {
            missing.sort(String::compareTo);
            if (!FMLEnvironment.production) {
                com.bettercontent.latentchemlib.LatentChemlibMod.LOGGER.warn(
                    "Development runtime has no pack-provided custom AdPother pollutants; production validation would reject: {}", missing
                );
                return;
            }
            throw new IllegalStateException("Missing AdPother pollutant registrations for ChemLib gases: " + missing);
        }
    }
}
