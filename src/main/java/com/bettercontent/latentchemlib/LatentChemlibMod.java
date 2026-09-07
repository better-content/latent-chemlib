package com.bettercontent.latentchemlib;

import com.bettercontent.latentchemlib.data.LatentDataManager;
import com.bettercontent.latentchemlib.sim.GasEscapeHandler;
import com.bettercontent.latentchemlib.sim.NuclearSurfaceScanner;
import com.bettercontent.latentchemlib.sim.PlacedNuclearLifecycle;
import com.bettercontent.latentchemlib.sim.PlacedNuclearLootModifier;
import com.bettercontent.latentchemlib.sim.SimulationScheduler;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherPollutantValidation;
import com.mojang.serialization.Codec;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(LatentChemlibMod.MOD_ID)
public class LatentChemlibMod {
    public static final String MOD_ID = "latent_chemlib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MOD_ID);
    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> PLACED_NUCLEAR_LOOT_MODIFIER =
        LOOT_MODIFIER_SERIALIZERS.register("placed_nuclear_state", () -> PlacedNuclearLootModifier.CODEC);

    public LatentChemlibMod(FMLJavaModLoadingContext loadingContext) {
        IEventBus modBus = loadingContext.getModEventBus();
        LOOT_MODIFIER_SERIALIZERS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(this::addReloadListeners);
        MinecraftForge.EVENT_BUS.register(SimulationScheduler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(GasEscapeHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NuclearSurfaceScanner.INSTANCE);
        MinecraftForge.EVENT_BUS.register(PlacedNuclearLifecycle.INSTANCE);
        MinecraftForge.EVENT_BUS.register(AdpotherPollutantValidation.INSTANCE);
        LOGGER.info("Loaded {}", MOD_ID);
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(LatentDataManager.INSTANCE);
    }

}
