package com.bettercontent.latentchemlib;

import com.bettercontent.latentchemlib.block.ChemicalCloudBlock;
import com.bettercontent.latentchemlib.block.LatentMachineBlock;
import com.bettercontent.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.bettercontent.latentchemlib.blockentity.LatentMachineBlockEntity;
import com.bettercontent.latentchemlib.api.IChemicalStateHandler;
import com.bettercontent.latentchemlib.data.LatentDataManager;
import com.bettercontent.latentchemlib.item.ChemicalCellItem;
import com.bettercontent.latentchemlib.sim.GasEscapeHandler;
import com.bettercontent.latentchemlib.sim.NuclearSurfaceScanner;
import com.bettercontent.latentchemlib.sim.PlacedNuclearLifecycle;
import com.bettercontent.latentchemlib.sim.PlacedNuclearLootModifier;
import com.bettercontent.latentchemlib.sim.SimulationScheduler;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherPollutantValidation;
import com.bettercontent.latentchemlib.integration.adpother.GasFireballSourceEntity;
import com.mojang.serialization.Codec;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
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

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MOD_ID);
    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> PLACED_NUCLEAR_LOOT_MODIFIER =
        LOOT_MODIFIER_SERIALIZERS.register("placed_nuclear_state", () -> PlacedNuclearLootModifier.CODEC);

    public static final RegistryObject<Block> CHEMICAL_CLOUD = BLOCKS.register("chemical_cloud", () ->
        new ChemicalCloudBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .replaceable()
            .noCollission()
            .noOcclusion()
            .strength(0.05f)
            .lightLevel(ChemicalCloudBlock::lightLevel)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()));

    public static final RegistryObject<Block> GAS_CAPTURE = machine("gas_capture");
    public static final RegistryObject<Block> GAS_TANK = machine("gas_tank");
    public static final RegistryObject<Block> GAS_REACTION_CHAMBER = machine("gas_reaction_chamber");
    public static final RegistryObject<Block> GAS_RELEASE = machine("gas_release");
    public static final RegistryObject<Block> PNEUMATIC_CHEMICAL_TUBE = machine("pneumatic_chemical_tube");
    public static final RegistryObject<Block> DRY_AIR_SEPARATOR = machine("dry_air_separator");
    public static final RegistryObject<Item> SEALED_CHEMICAL_CELL =
        ITEMS.register("sealed_chemical_cell", () -> new ChemicalCellItem(new Item.Properties()));

    public static final RegistryObject<BlockEntityType<ChemicalCloudBlockEntity>> CHEMICAL_CLOUD_ENTITY =
        BLOCK_ENTITIES.register("chemical_cloud", () ->
            BlockEntityType.Builder.of(ChemicalCloudBlockEntity::new, CHEMICAL_CLOUD.get()).build(null));

    public static final RegistryObject<BlockEntityType<LatentMachineBlockEntity>> MACHINE_ENTITY =
        BLOCK_ENTITIES.register("latent_machine", () ->
            BlockEntityType.Builder.of(
                LatentMachineBlockEntity::new,
                GAS_CAPTURE.get(),
                GAS_TANK.get(),
                GAS_REACTION_CHAMBER.get(),
                GAS_RELEASE.get(),
                PNEUMATIC_CHEMICAL_TUBE.get(),
                DRY_AIR_SEPARATOR.get()
            ).build(null));

    public static final RegistryObject<EntityType<GasFireballSourceEntity>> GAS_FIREBALL_SOURCE =
        ENTITY_TYPES.register("gas_fireball_source", () -> EntityType.Builder
            .<GasFireballSourceEntity>of(GasFireballSourceEntity::new, MobCategory.MISC)
            .sized(0.01f, 0.01f)
            .clientTrackingRange(0)
            .updateInterval(Integer.MAX_VALUE)
            .noSummon()
            .noSave()
            .build(MOD_ID + ":gas_fireball_source"));

    public LatentChemlibMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
        LOOT_MODIFIER_SERIALIZERS.register(modBus);
        modBus.addListener(this::registerCapabilities);
        MinecraftForge.EVENT_BUS.addListener(this::addReloadListeners);
        MinecraftForge.EVENT_BUS.register(SimulationScheduler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(GasEscapeHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NuclearSurfaceScanner.INSTANCE);
        MinecraftForge.EVENT_BUS.register(PlacedNuclearLifecycle.INSTANCE);
        MinecraftForge.EVENT_BUS.register(AdpotherPollutantValidation.INSTANCE);
        LOGGER.info("Loaded {}", MOD_ID);
    }

    private static RegistryObject<Block> machine(String name) {
        RegistryObject<Block> block = BLOCKS.register(name, () ->
            new LatentMachineBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(4.0f)
                .requiresCorrectToolForDrops()));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(LatentDataManager.INSTANCE);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IChemicalStateHandler.class);
    }
}
