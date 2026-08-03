package com.gerald.latentchemlib.gametest;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.api.LatentCapabilities;
import com.mojang.authlib.GameProfile;
import com.endertech.minecraft.mods.adpother.AdPother;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adchimneys.AdChimneys;
import com.endertech.minecraft.mods.adchimneys.blocks.Chimney;
import com.endertech.minecraft.mods.adchimneys.blocks.Pipe;
import com.endertech.minecraft.mods.adchimneys.blocks.Pump;
import com.endertech.minecraft.mods.adchimneys.blocks.Vent;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.gerald.latentchemlib.blockentity.LatentMachineBlockEntity;
import com.gerald.latentchemlib.item.ChemicalCellItem;
import com.gerald.latentchemlib.integration.adpother.AdpotherCloudView;
import com.gerald.latentchemlib.integration.adpother.AdpotherRoutingProbe;
import com.gerald.latentchemlib.integration.adpother.LatentGasHazardService;
import com.gerald.latentchemlib.integration.pneumatic.DryAirSeparation;
import com.gerald.latentchemlib.integration.pneumatic.PneumaticChemistryMode;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.GasFluidCodec;
import com.gerald.latentchemlib.sim.GasEscapeHandler;
import com.gerald.latentchemlib.sim.NuclearPhenomenaMath;
import com.gerald.latentchemlib.sim.NuclearSimulationService;
import com.gerald.latentchemlib.sim.NuclearStackData;
import com.gerald.latentchemlib.sim.NuclearSurfaceScanner;
import com.gerald.latentchemlib.sim.RadioactiveFormResolver;
import com.gerald.latentchemlib.sim.PlacedNuclearData;
import com.gerald.latentchemlib.sim.PlacedNuclearLifecycle;
import com.gerald.latentchemlib.sim.SimulationBudget;
import com.gerald.latentchemlib.sim.SimulationScheduler;
import com.gerald.latentchemlib.data.LatentDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(LatentChemlibMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LatentChemlibGameTests {
    private LatentChemlibGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void registeredBlocksCreateExpectedBlockEntities(GameTestHelper helper) {
        BlockPos cloudPos = new BlockPos(1, 1, 1);
        helper.setBlock(cloudPos, LatentChemlibMod.CHEMICAL_CLOUD.get());
        helper.assertTrue(helper.getBlockEntity(cloudPos) instanceof ChemicalCloudBlockEntity, "Chemical cloud should create its block entity");

        assertMachineEntity(helper, new BlockPos(2, 1, 1), LatentChemlibMod.GAS_CAPTURE.get());
        assertMachineEntity(helper, new BlockPos(3, 1, 1), LatentChemlibMod.GAS_TANK.get());
        assertMachineEntity(helper, new BlockPos(4, 1, 1), LatentChemlibMod.GAS_REACTION_CHAMBER.get());
        assertMachineEntity(helper, new BlockPos(5, 1, 1), LatentChemlibMod.GAS_RELEASE.get());
        assertMachineEntity(helper, new BlockPos(6, 1, 1), LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get());
        assertMachineEntity(helper, new BlockPos(7, 1, 1), LatentChemlibMod.DRY_AIR_SEPARATOR.get());
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void pneumaticChemicalTubeSelectsExactlyOneTransportAuthority(GameTestHelper helper) {
        LatentMachineBlockEntity tube = placeMachine(
            helper, new BlockPos(1, 1, 1), LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get()
        );
        tube.pneumaticAirHandler().addAir(1_500);

        helper.assertTrue(tube.transportMode() == PneumaticChemistryMode.AIR, "New and legacy-unspecified tubes must default to native air mode");
        helper.assertTrue(tube.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY).isPresent(), "Air mode must expose PNCR's native air capability");
        helper.assertTrue(!tube.getCapability(LatentCapabilities.CHEMICAL_STATE).isPresent(), "Air mode must not expose Latent chemical matter");

        tube.setTransportMode(PneumaticChemistryMode.CHEMICAL);
        helper.assertTrue(!tube.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY).isPresent(), "Chemical mode must isolate PNCR air");
        helper.assertTrue(tube.getCapability(LatentCapabilities.CHEMICAL_STATE).isPresent(), "Chemical mode must expose full Latent mixture state");
        helper.assertTrue(tube.pneumaticAirHandler().getAir() == 1_500, "Changing mode must not migrate or destroy native compressed air");

        tube.setTransportMode(PneumaticChemistryMode.AIR);
        helper.assertTrue(tube.pneumaticAirHandler().getAir() == 1_500, "Returning to air mode must reveal the untouched native ledger");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void pneumaticChemicalTubeAirModeJoinsNativePressureNetwork(GameTestHelper helper) {
        BlockPos boundaryPos = new BlockPos(1, 1, 1);
        BlockPos pressureTubePos = boundaryPos.east();
        LatentMachineBlockEntity boundary = placeMachine(helper, boundaryPos, LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get());
        Block pressureTube = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft", "pressure_tube"));
        helper.assertTrue(pressureTube != null && pressureTube != Blocks.AIR, "PNCR pressure tube must be registered");
        helper.setBlock(pressureTubePos, pressureTube);
        boundary.pneumaticAirHandler().addAir(2_000);

        helper.succeedWhen(() -> {
            BlockEntity pressureTubeEntity = helper.getBlockEntity(pressureTubePos);
            helper.assertTrue(pressureTubeEntity != null, "PNCR pressure tube must create a block entity");
            var air = pressureTubeEntity.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY).orElseThrow(AssertionError::new);
            helper.assertTrue(air.getAir() > 0, "Native PNCR dispersion must move compressed air into its pressure tube");
            helper.assertTrue(boundary.pneumaticAirHandler().getAir() < 2_000, "Boundary must debit the same native air ledger");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 100)
    public static void pneumaticChemicalTubesMoveCompleteMixturesWithoutSpeciesLoss(GameTestHelper helper) {
        LatentMachineBlockEntity source = placeMachine(
            helper, new BlockPos(1, 1, 1), LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get()
        );
        LatentMachineBlockEntity target = placeMachine(
            helper, new BlockPos(2, 1, 1), LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get()
        );
        source.setTransportMode(PneumaticChemistryMode.CHEMICAL);
        target.setTransportMode(PneumaticChemistryMode.CHEMICAL);
        source.setStoredState(
            new ChemicalState("chemlib:nitrogen", 96.0, 3.0, 293.15, 0.0, 0.0)
                .merge(new ChemicalState("chemlib:oxygen", 32.0, 1.0, 293.15, 0.0, 0.0))
        );

        helper.succeedWhen(() -> {
            helper.assertTrue(target.storedState().mass() > 0.0, "Adjacent chemical-mode tubes must exchange Latent matter");
            helper.assertTrue(target.storedState().massOf("chemlib:nitrogen") > 0.0, "Transferred mixture must retain nitrogen");
            helper.assertTrue(target.storedState().massOf("chemlib:oxygen") > 0.0, "Transferred mixture must retain oxygen");
            helper.assertTrue(Math.abs(source.storedState().massOf("chemlib:nitrogen") + target.storedState().massOf("chemlib:nitrogen") - 96.0) < 1.0e-9,
                "Chemical tube transfer must conserve nitrogen");
            helper.assertTrue(Math.abs(source.storedState().massOf("chemlib:oxygen") + target.storedState().massOf("chemlib:oxygen") - 32.0) < 1.0e-9,
                "Chemical tube transfer must conserve oxygen");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void dryAirSeparatorConsumesFiniteNativeAirIntoCanonicalMixture(GameTestHelper helper) {
        LatentMachineBlockEntity separator = placeMachine(
            helper, new BlockPos(1, 1, 1), LatentChemlibMod.DRY_AIR_SEPARATOR.get()
        );
        int initialAir = 2_000;
        separator.pneumaticAirHandler().addAir(initialAir);

        helper.succeedWhen(() -> {
            ChemicalState output = separator.storedState();
            helper.assertTrue(output.mass() >= DryAirSeparation.OUTPUT_MASS, "Separator should emit at least one dry-air batch");
            int consumedAir = initialAir - separator.pneumaticAirHandler().getAir();
            helper.assertTrue(consumedAir >= DryAirSeparation.AIR_PER_BATCH, "Separator must consume native PNCR air");
            helper.assertTrue(consumedAir % DryAirSeparation.AIR_PER_BATCH == 0, "Only complete finite air batches may be consumed");
            helper.assertTrue(Math.abs(output.mass() - consumedAir * DryAirSeparation.OUTPUT_MASS / DryAirSeparation.AIR_PER_BATCH) < 1.0e-9,
                "Every output batch must correspond to consumed native air");
            helper.assertTrue(output.massOf("chemlib:nitrogen") > output.massOf("chemlib:oxygen"), "Canonical dry air must be nitrogen-dominant");
            helper.assertTrue(output.massOf("chemlib:carbon_dioxide") > 0.0, "Canonical dry air must retain its carbon dioxide trace");
            helper.assertTrue(separator.getCapability(LatentCapabilities.CHEMICAL_STATE).isPresent(), "Mixture output must use Latent's multi-species capability");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void adpotherEmissionCreatesLatentCloudInsteadOfAdpotherGas(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Pollutant<?> carbon = AdPother.getInstance().pollutants.findByName("carbon")
            .orElseThrow(() -> new AssertionError("AdPother carbon selector must be registered"));

        int emitted = carbon.generateAt(helper.getLevel(), helper.absolutePos(pos), 2, 1);

        helper.assertTrue(emitted == 2, "AdPother should report both units accepted by Latent");
        helper.assertTrue(
            helper.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity,
            "AdPother emission should create the Latent chemical cloud block entity"
        );
        ChemicalCloudBlockEntity cloud = (ChemicalCloudBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(
            cloud.chemicalState().chemicalId().equals("chemlib:carbon_dioxide"),
            "Legacy carbon emissions should bridge to ChemLib carbon dioxide"
        );
        helper.assertTrue(cloud.chemicalState().mass() == 32.0, "Two AdPother units should equal 32 Latent mass");
        helper.assertTrue(
            !helper.getBlockState(pos).is(carbon),
            "The integration must not place an AdPother gas block"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "advancedChimneysRouting", timeoutTicks = 40)
    public static void advancedChimneyRoutesConfiguredEmitterIntoLatentMass(GameTestHelper helper) {
        assertAdvancedChimneysRoute(helper, AdvancedChimneysRoute.CHIMNEY);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "advancedChimneysRouting", timeoutTicks = 40)
    public static void advancedVentRoutesConfiguredEmitterIntoLatentMass(GameTestHelper helper) {
        assertAdvancedChimneysRoute(helper, AdvancedChimneysRoute.VENT);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "advancedChimneysRouting", timeoutTicks = 40)
    public static void advancedPumpRoutesConfiguredEmitterIntoLatentMass(GameTestHelper helper) {
        assertAdvancedChimneysRoute(helper, AdvancedChimneysRoute.PUMP);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "advancedChimneysRouting", timeoutTicks = 40)
    public static void advancedPipeRoutesConfiguredEmitterIntoLatentMass(GameTestHelper helper) {
        assertAdvancedChimneysRoute(helper, AdvancedChimneysRoute.PIPE);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void adpotherExposureReadsOnlyWholeUnitsInTheSampledCloudCell(GameTestHelper helper) {
        BlockPos cloudPos = new BlockPos(1, 1, 1);
        ChemicalCloudBlockEntity cloud = placeCloud(helper, cloudPos);
        cloud.seed(new ChemicalState("chemlib:carbon_dioxide", 32.0, 1.0, 293.0, 0.0, 0.0));
        BlockPos absoluteCloudPos = helper.absolutePos(cloudPos);

        var contact = AdpotherCloudView.INSTANCE.contactAt(
            helper.getLevel(),
            absoluteCloudPos,
            net.minecraft.world.phys.Vec3.atCenterOf(absoluteCloudPos)
        ).orElseThrow();
        helper.assertTrue(contact.units() == 2, "Cloud contact should expose two whole AdPother units");
        helper.assertTrue(
            AdpotherCloudView.INSTANCE.contactAt(
                helper.getLevel(),
                absoluteCloudPos.east(),
                net.minecraft.world.phys.Vec3.atCenterOf(absoluteCloudPos.east())
            ).isEmpty(),
            "An adjacent cell must not inherit cloud exposure"
        );

        cloud.extractMass(17.0);
        helper.assertTrue(
            AdpotherCloudView.INSTANCE.contactAt(
                helper.getLevel(),
                absoluteCloudPos,
                net.minecraft.world.phys.Vec3.atCenterOf(absoluteCloudPos)
            ).isEmpty(),
            "A sub-unit cloud wisp must not apply AdPother exposure"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void adpotherCloudViewDoesNotLoadAbsentChunks(GameTestHelper helper) {
        BlockPos absentChunkPos = new BlockPos(29_999_984, 64, 29_999_984);
        helper.assertTrue(
            helper.getLevel().getChunkSource().getChunkNow(
                absentChunkPos.getX() >> 4,
                absentChunkPos.getZ() >> 4
            ) == null,
            "The regression fixture must begin outside the loaded chunk set"
        );
        helper.assertTrue(
            AdpotherCloudView.INSTANCE.contactAt(
                helper.getLevel(),
                absentChunkPos,
                net.minecraft.world.phys.Vec3.atCenterOf(absentChunkPos)
            ).isEmpty(),
            "Contact lookup should return empty for an absent chunk"
        );
        helper.assertTrue(
            helper.getLevel().getChunkSource().getChunkNow(
                absentChunkPos.getX() >> 4,
                absentChunkPos.getZ() >> 4
            ) == null,
            "Contact lookup must not materialize an absent chunk"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void mixedFlammableCloudsShareOneExplosionThreshold(GameTestHelper helper) {
        BlockPos firstPos = new BlockPos(1, 1, 1);
        BlockPos secondPos = firstPos.east();
        placeCloud(helper, firstPos).seed(
            new ChemicalState("chemlib:carbon_dioxide", 8.0 * 16.0, 1.0, 293.0, 0.0, 0.0)
        );
        placeCloud(helper, secondPos).seed(
            new ChemicalState("latent_chemlib:dust", 10.0 * 16.0, 1.0, 293.0, 0.0, 0.0)
        );

        var detection = LatentGasHazardService.INSTANCE.detectAround(
            helper.getLevel(),
            helper.absolutePos(firstPos),
            4
        );
        helper.assertTrue(
            detection.explosionRisk(),
            "Half of carbon's LEL plus half of dust's LEL should be an explosion risk"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void ignitionConsumesThresholdQualifiedLatentCloud(GameTestHelper helper) {
        BlockPos cloudPos = new BlockPos(2, 2, 2);
        placeCloud(helper, cloudPos).seed(
            new ChemicalState("latent_chemlib:dust", 20.0 * 16.0, 1.0, 293.0, 0.0, 0.0)
        );
        helper.setBlock(cloudPos.east(), Blocks.TORCH);

        boolean ignited = LatentGasHazardService.INSTANCE.tryIgnite(
            helper.getLevel(),
            helper.absolutePos(cloudPos)
        );

        helper.assertTrue(ignited, "A threshold dust cloud beside a torch should ignite");
        helper.assertTrue(
            !(helper.getBlockEntity(cloudPos) instanceof ChemicalCloudBlockEntity),
            "Ignition must consume the assessed cloud before creating the explosion"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void chemicalCloudSeedMergeAndExtractPreservesMixtureLedger(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChemicalCloudBlockEntity cloud = placeCloud(helper, pos);
        cloud.seed(new ChemicalState("chemlib:hydrogen", 100.0, 1.0, 400.0, 0.2, 20.0));
        cloud.seed(new ChemicalState("chemlib:hydrogen", 300.0, 3.0, 800.0, 0.6, 60.0));

        ChemicalState merged = cloud.chemicalState();
        helper.assertTrue(merged.mass() == 400.0, "Matching chemical clouds should merge mass");
        helper.assertTrue(merged.temperature() == 700.0, "Merged cloud should weight temperature by mass");

        cloud.seed(new ChemicalState("chemlib:helium", 1_000.0, 10.0, 100.0, 0.0, 0.0));
        helper.assertTrue(cloud.chemicalState().massOf("chemlib:hydrogen") == 400.0, "Mixture must retain the original component mass");
        helper.assertTrue(cloud.chemicalState().massOf("chemlib:helium") == 1_000.0, "Mixture must retain the incoming component mass");
        helper.assertTrue(cloud.chemicalState().mass() == 1_400.0, "Unlike species must merge without loss");

        ChemicalState extracted = cloud.extractMass(150.0);
        helper.assertTrue(extracted.mass() == 150.0, "Extracted cloud state should cap to requested mass");
        helper.assertTrue(cloud.chemicalState().mass() == 1_250.0, "Cloud should retain remaining mass after extraction");
        helper.assertTrue(extracted.massOf("chemlib:hydrogen") + cloud.chemicalState().massOf("chemlib:hydrogen") == 400.0,
            "Proportional extraction must conserve hydrogen");
        helper.assertTrue(extracted.massOf("chemlib:helium") + cloud.chemicalState().massOf("chemlib:helium") == 1_000.0,
            "Proportional extraction must conserve helium");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void gasCapturePullsMatterFromAdjacentCloud(GameTestHelper helper) {
        BlockPos capturePos = new BlockPos(1, 1, 1);
        BlockPos cloudPos = new BlockPos(2, 1, 1);
        LatentMachineBlockEntity capture = placeMachine(helper, capturePos, LatentChemlibMod.GAS_CAPTURE.get());
        ChemicalCloudBlockEntity cloud = placeCloud(helper, cloudPos);
        cloud.seed(new ChemicalState("chemlib:hydrogen", 1_000.0, 4.0, 600.0, 0.4, 200.0));

        helper.succeedWhen(() -> {
            helper.assertTrue(capture.storedState().mass() > 0.0, "Gas capture should pull matter from an adjacent cloud");
            helper.assertTrue(totalCloudMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4)) + capture.storedState().mass() > 900.0, "Captured matter should remain mostly conserved across nearby cloud diffusion");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void gasReleaseCreatesCloudAboveAndConsumesStorage(GameTestHelper helper) {
        BlockPos releasePos = new BlockPos(1, 1, 1);
        BlockPos cloudPos = releasePos.above();
        LatentMachineBlockEntity release = placeMachine(helper, releasePos, LatentChemlibMod.GAS_RELEASE.get());
        release.setStoredState(new ChemicalState("chemlib:helium", 300.0, 2.0, 500.0, 0.1, 120.0));

        helper.succeedWhen(() -> {
            BlockEntity blockEntity = helper.getBlockEntity(cloudPos);
            helper.assertTrue(blockEntity instanceof ChemicalCloudBlockEntity, "Gas release should create a cloud above itself");
            ChemicalCloudBlockEntity cloud = (ChemicalCloudBlockEntity) blockEntity;
            helper.assertTrue(cloud.chemicalState().chemicalId().equals("chemlib:helium"), "Gas release should seed a matching cloud above itself");
            helper.assertTrue(cloud.chemicalState().mass() > 0.0, "Gas release should move stored matter into a cloud");
            helper.assertTrue(release.storedState().mass() < 300.0, "Gas release should consume storage");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void gasCaptureStoresAdjacentChemicalAsMixture(GameTestHelper helper) {
        BlockPos capturePos = new BlockPos(1, 1, 1);
        BlockPos cloudPos = new BlockPos(2, 1, 1);
        LatentMachineBlockEntity capture = placeMachine(helper, capturePos, LatentChemlibMod.GAS_CAPTURE.get());
        capture.setStoredState(new ChemicalState("chemlib:helium", 200.0, 1.0, 300.0, 0.0, 20.0));
        ChemicalCloudBlockEntity cloud = placeCloud(helper, cloudPos);
        cloud.seed(new ChemicalState("chemlib:hydrogen", 800.0, 4.0, 600.0, 0.4, 120.0));

        helper.runAfterDelay(21, () -> {
            helper.assertTrue(capture.storedState().massOf("chemlib:helium") == 200.0, "Capture must retain its existing component");
            helper.assertTrue(capture.storedState().massOf("chemlib:hydrogen") > 0.0, "Capture must accept a second component into its mixture");
            helper.assertTrue(totalCloudMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4)) + capture.storedState().mass() > 900.0,
                    "Mixed capture must mostly conserve aggregate matter");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void gasReleaseMergesWithDifferentChemicalCloudAbove(GameTestHelper helper) {
        BlockPos releasePos = new BlockPos(1, 1, 1);
        BlockPos cloudPos = releasePos.above();
        LatentMachineBlockEntity release = placeMachine(helper, releasePos, LatentChemlibMod.GAS_RELEASE.get());
        ChemicalCloudBlockEntity cloud = placeCloud(helper, cloudPos);
        release.setStoredState(new ChemicalState("chemlib:helium", 300.0, 2.0, 500.0, 0.1, 120.0));
        cloud.seed(new ChemicalState("chemlib:hydrogen", 400.0, 4.0, 650.0, 0.3, 160.0));

        helper.runAfterDelay(21, () -> {
            helper.assertTrue(totalCloudChemicalMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4), "chemlib:hydrogen") > 300.0,
                    "Release must retain matter already present in the cloud field");
            helper.assertTrue(totalCloudChemicalMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4), "chemlib:helium") > 0.0,
                    "Release must merge its component into the occupied cloud field");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 120)
    public static void chemicalCloudDiffusesIntoOpenAirWithoutLosingMostMass(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 2, 2);
        ChemicalCloudBlockEntity cloud = placeCloud(helper, origin);
        cloud.seed(new ChemicalState("chemlib:hydrogen", 1_200.0, 8.0, 700.0, 0.2, 200.0));

        helper.succeedWhen(() -> {
            double totalMass = totalCloudMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
            helper.assertTrue(totalMass > 1_000.0, "Diffusion should conserve most mass while clouds spread");
            helper.assertTrue(totalCloudCount(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4)) > 1, "Diffusion should spread gas into neighboring cells");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 320)
    public static void chemicalCloudEventuallyDissipatesWhenBoxedIn(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 2, 2);
        for (BlockPos neighbor : new BlockPos[] {
            origin.north(),
            origin.south(),
            origin.east(),
            origin.west(),
            origin.above(),
            origin.below()
        }) {
            helper.setBlock(neighbor, Blocks.STONE);
        }

        ChemicalCloudBlockEntity cloud = placeCloud(helper, origin);
        cloud.seed(new ChemicalState("chemlib:helium", 200.0, 2.0, 320.0, 0.0, 0.0));

        helper.succeedWhen(() ->
            helper.assertTrue(helper.getBlockState(origin).isAir(), "A boxed-in cloud should eventually dissipate instead of persisting forever")
        );
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void reactionChamberAgitatesStoredMatter(GameTestHelper helper) {
        BlockPos chamberPos = new BlockPos(1, 1, 1);
        LatentMachineBlockEntity chamber = placeMachine(helper, chamberPos, LatentChemlibMod.GAS_REACTION_CHAMBER.get());
        chamber.setStoredState(new ChemicalState("chemlib:hydrogen", 125.0, 1.0, 300.0, 0.0, 25.0));

        helper.succeedWhen(() -> {
            ChemicalState state = chamber.storedState();
            helper.assertTrue(state.temperature() > 300.0, "Reaction chamber should heat stored matter");
            helper.assertTrue(state.charge() > 0.0, "Reaction chamber should increase charge");
            helper.assertTrue(state.energy() > 25.0, "Reaction chamber should add energy");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void sealedChemicalCellStoresChemicalState(GameTestHelper helper) {
        ItemStack empty = new ItemStack(LatentChemlibMod.SEALED_CHEMICAL_CELL.get());
        ChemicalState state = new ChemicalState("chemlib:hydrogen", 250.0, 2.0, 500.0, 0.5, 1000.0);
        ItemStack filled = ChemicalCellItem.withState(empty, state);

        helper.assertTrue(ChemicalCellItem.hasState(filled), "Filled cell should carry chemical state NBT");
        helper.assertTrue(ChemicalCellItem.state(filled).equals(state), "Filled cell should round-trip chemical state");
        helper.assertTrue(!ChemicalCellItem.hasState(ChemicalCellItem.withState(filled, ChemicalState.empty())), "Empty cell should clear chemical state NBT");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void sealedChemicalCellFluidCapabilityUsesFixedCapacityAndPreservesState(GameTestHelper helper) {
        ItemStack cell = new ItemStack(LatentChemlibMod.SEALED_CHEMICAL_CELL.get());
        IFluidHandler handler = cell.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(AssertionError::new);
        FluidStack hydrogen = new FluidStack(GasFluidCodec.sourceFluid("chemlib:hydrogen").orElseThrow(), 4_500);

        helper.assertTrue(handler.fill(hydrogen, IFluidHandler.FluidAction.EXECUTE) == 4_000, "Cell should cap gas fill at 4,000 mB");
        helper.assertTrue(ChemicalCellItem.state(cell).mass() == 256.0, "Full cell should store exactly 256 mass");
        FluidStack drained = handler.drain(250, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(drained.getAmount() == 250, "Cell should drain a requested formula unit");
        helper.assertTrue(ChemicalCellItem.state(cell).mass() == 240.0, "Draining 250 mB should remove exactly 16 mass");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void machineFluidCapabilitiesEnforceContainmentRoles(GameTestHelper helper) {
        FluidStack hydrogen = new FluidStack(GasFluidCodec.sourceFluid("chemlib:hydrogen").orElseThrow(), 250);
        LatentMachineBlockEntity capture = placeMachine(helper, new BlockPos(1, 1, 1), LatentChemlibMod.GAS_CAPTURE.get());
        LatentMachineBlockEntity tank = placeMachine(helper, new BlockPos(2, 1, 1), LatentChemlibMod.GAS_TANK.get());
        LatentMachineBlockEntity chamber = placeMachine(helper, new BlockPos(3, 1, 1), LatentChemlibMod.GAS_REACTION_CHAMBER.get());
        LatentMachineBlockEntity release = placeMachine(helper, new BlockPos(4, 1, 1), LatentChemlibMod.GAS_RELEASE.get());
        IFluidHandler captureFluid = fluidHandler(capture);
        IFluidHandler tankFluid = fluidHandler(tank);
        IFluidHandler chamberFluid = fluidHandler(chamber);
        IFluidHandler releaseFluid = fluidHandler(release);

        helper.assertTrue(captureFluid.fill(hydrogen, IFluidHandler.FluidAction.EXECUTE) == 0, "Capture must reject external gas fill");
        capture.setStoredState(new ChemicalState("chemlib:hydrogen", 16.0, 1.0, 293.0, 0.0, 0.0));
        helper.assertTrue(captureFluid.drain(250, IFluidHandler.FluidAction.SIMULATE).getAmount() == 250, "Capture must expose collected gas for drain");
        helper.assertTrue(tankFluid.fill(hydrogen, IFluidHandler.FluidAction.EXECUTE) == 250, "Tank must accept gas");
        helper.assertTrue(tankFluid.drain(250, IFluidHandler.FluidAction.SIMULATE).getAmount() == 250, "Tank must expose gas");
        helper.assertTrue(chamberFluid.fill(hydrogen, IFluidHandler.FluidAction.EXECUTE) == 250, "Reaction chamber must accept gas");
        helper.assertTrue(chamberFluid.drain(250, IFluidHandler.FluidAction.SIMULATE).getAmount() == 250, "Reaction chamber must expose gas");
        helper.assertTrue(releaseFluid.fill(hydrogen, IFluidHandler.FluidAction.EXECUTE) == 250, "Release must accept gas");
        helper.assertTrue(releaseFluid.drain(250, IFluidHandler.FluidAction.SIMULATE).isEmpty(), "Release must not expose gas for drain");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void placedGasFluidImmediatelyBecomesChemicalCloud(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        var hydrogen = GasFluidCodec.sourceFluid("chemlib:hydrogen").orElseThrow();
        helper.assertTrue(
            GasFluidCodec.chemicalId(((FlowingFluid) hydrogen).getFlowing()).orElseThrow().equals("chemlib:hydrogen"),
            "Flowing and source gas fluid IDs must resolve to the same chemical"
        );
        helper.setBlock(pos, hydrogen.defaultFluidState().createLegacyBlock());
        helper.assertTrue(helper.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity, "Placed gas fluid must immediately gasify");
        helper.assertTrue(!GasFluidCodec.isGasFluid(helper.getLevel().getFluidState(helper.absolutePos(pos)).getType()), "No gas fluid block may remain after conversion");
        ChemicalCloudBlockEntity cloud = (ChemicalCloudBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(cloud.chemicalState().chemicalId().equals("chemlib:hydrogen"), "Gasified fluid must preserve chemical identity");
        helper.assertTrue(cloud.chemicalState().mass() == 64.0, "One placed bucket must become exactly 64 mass");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 60)
    public static void blockInventoryGasEscapesWithinTwentyTicks(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(1, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("chemlib", "hydrogen"))));

        helper.runAfterDelay(21, () -> {
            helper.assertTrue(chest.getItem(0).isEmpty(), "Loose gas must leave block inventories within 20 ticks");
            helper.assertTrue(totalCloudMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4)) > 0.0, "Escaped inventory gas must become a cloud");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 100)
    public static void ordinaryContainedStateBecomesCriticalOnlyWithLocalConditions(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(3, 1, 1);
        LatentMachineBlockEntity tank = placeMachine(helper, tankPos, LatentChemlibMod.GAS_TANK.get());
        tank.setStoredState(new ChemicalState("chemlib:californium", 1_000.0, 8.0, 900.0, 0.0, 0.0));
        helper.setBlock(tankPos.west(), Blocks.WATER);
        helper.setBlock(tankPos.east(), Blocks.STONE);

        helper.succeedWhen(() -> {
            helper.assertTrue(tank.storedState().massOf("chemlib:barium") > 0.0, "Critical material must retain its heavy daughter");
            helper.assertTrue(tank.storedState().massOf("chemlib:krypton") > 0.0, "Critical material must retain its light daughter");
            helper.assertTrue(tank.getHeat() > 0.0f, "Ordinary containment must receive HeatSync heat from fission");
            helper.assertBlockPresent(Blocks.LAVA, tankPos.east());
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 100)
    public static void configuredHeavyUnstableStateContinuouslyHeatsAdjacentMaterial(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 1, 1);
        LatentMachineBlockEntity source = placeMachine(helper, sourcePos, LatentChemlibMod.GAS_TANK.get());
        LatentMachineBlockEntity adjacent = placeMachine(helper, sourcePos.east(), LatentChemlibMod.GAS_TANK.get());
        source.setStoredState(new ChemicalState("chemlib:bismuth", 1_000.0, 8.0, 600.0, 0.0, 0.0));

        helper.succeedWhen(() -> {
            helper.assertTrue(source.storedState().massOf("chemlib:thallium") > 0.0,
                "Loaded Bi-209 decay evidence must drive deterministic daughter formation");
            helper.assertTrue(source.getHeat() > 0.0f, "The containing material must receive conserved decay heat");
            helper.assertTrue(adjacent.getHeat() > 0.0f, "Touching HeatSync material must receive a non-duplicated share of decay heat");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 100)
    public static void opposingHotDenseGasCloudsFuseAtCollisionCell(GameTestHelper helper) {
        BlockPos collisionPos = new BlockPos(3, 1, 1);
        ChemicalCloudBlockEntity collision = placeCloud(helper, collisionPos);
        ChemicalCloudBlockEntity west = placeCloud(helper, collisionPos.west());
        ChemicalCloudBlockEntity east = placeCloud(helper, collisionPos.east());
        collision.seed(new ChemicalState("chemlib:argon", 100.0, 10.0, 9_000.0, 0.0, 100_000.0));
        west.seed(new ChemicalState("chemlib:hydrogen", 100.0, 12.0, 9_000.0, 2.0, 100_000.0));
        east.seed(new ChemicalState("chemlib:hydrogen", 100.0, 12.0, 9_000.0, 2.0, 100_000.0));
        helper.setBlock(collisionPos.above(), Blocks.STONE);
        helper.assertTrue(NuclearPhenomenaMath.fusion(
            west.chemicalState(), east.chemicalState(),
            LatentDataManager.INSTANCE.traits("chemlib:hydrogen"), true,
            LatentDataManager.INSTANCE.nuclearPhenomenaProfile()
        ).isPresent(), "Configured live stream states must cross the fusion barrier");

        helper.succeedWhen(() -> {
            helper.assertTrue(collision.chemicalState().massOf("chemlib:helium") > 0.0,
                "Opposing compatible streams must create helium in the collision cell");
            helper.assertTrue(Math.abs(west.chemicalState().mass() - 96.0) < 1.0e-6
                    && Math.abs(east.chemicalState().mass() - 96.0) < 1.0e-6,
                "One collision must debit exactly one configured batch from both streams");
            helper.assertBlockPresent(Blocks.LAVA, collisionPos.above());
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 80)
    public static void configuredRadioactiveChemLibStackSurvivesAndAdvectsInLava(GameTestHelper helper) {
        ItemStack bismuth = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("chemlib", "bismuth")));
        helper.assertTrue(!bismuth.isEmpty(), "Configured radioactive ChemLib bismuth must be registered");
        BlockPos lavaPos = new BlockPos(2, 2, 2);
        helper.setBlock(lavaPos.below(), Blocks.STONE);
        helper.setBlock(lavaPos.east().below(), Blocks.STONE);
        helper.setBlock(lavaPos.east(2), Blocks.STONE);
        helper.setBlock(lavaPos, Blocks.LAVA);
        helper.setBlock(lavaPos.east(), Fluids.LAVA.getFlowing(7, false).createLegacyBlock());
        var initialFlow = helper.getLevel().getFluidState(helper.absolutePos(lavaPos))
            .getFlow(helper.getLevel(), helper.absolutePos(lavaPos));
        helper.assertTrue(initialFlow.horizontalDistance() > 0.01, "The live lava test must create a real horizontal flow field");
        ItemEntity item = helper.spawnItem(bismuth.getItem(), lavaPos);
        item.setNoGravity(true);
        double startX = item.getX();
        double startZ = item.getZ();

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(item.isAlive(), "Loaded isotope evidence must keep radioactive matter alive in actual lava");
            double horizontalTravel = Math.hypot(item.getX() - startX, item.getZ() - startZ);
            helper.assertTrue(horizontalTravel > 0.01 || item.getDeltaMovement().horizontalDistance() > 0.01,
                "The active dropped-item scanner must advect radioactive matter with lava flow");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 40)
    public static void everyConfiguredRadioactiveRegistrationResolvesByIsotopeAndFormData(GameTestHelper helper) {
        Set<String> expected = new LinkedHashSet<>();
        LatentDataManager.INSTANCE.nuclearDecayRules().forEach(decay -> {
            expected.add(decay.inputChemical());
            ResourceLocation base = ResourceLocation.tryParse(decay.inputChemical());
            if (base == null) return;
            LatentDataManager.INSTANCE.nuclearFormRules().forEach(form -> {
                ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + form.suffix());
                if (ForgeRegistries.ITEMS.containsKey(candidate)) expected.add(candidate.toString());
            });
        });
        helper.assertTrue(expected.size() == 79, "The live isotope/form cross-product must contain the audited 79 registrations, got " + expected.size());
        for (String id : expected) {
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id)));
            var resolved = RadioactiveFormResolver.INSTANCE.resolve(stack);
            helper.assertTrue(resolved.isPresent(), "Radioactive form must resolve: " + id);
            helper.assertTrue(resolved.get().isotopeMassNumber() >= 209, "Resolved form must satisfy the configured heavy-isotope threshold: " + id);
            if (id.equals("chemlib:bismuth") || id.equals("chemlib:bismuth_dust") || id.equals("chemlib:bismuth_ingot") || id.equals("chemlib:bismuth_plate")) {
                helper.assertTrue(resolved.get().unitMass() == 209.0, "Bi-209 unit mass must use isotope A=209, not atomic number Z=83: " + id);
            }
            if (id.equals("chemlib:bismuth_metal_block")) {
                helper.assertTrue(resolved.get().unitMass() == 1881.0, "A metal block must contain nine isotope-scaled material units");
            }
            if (id.equals("chemlib:bismuth_nugget")) {
                helper.assertTrue(Math.abs(resolved.get().unitMass() - (209.0 / 9.0)) < 1.0e-9,
                    "A nugget must contain one ninth of an isotope-scaled material unit");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 80)
    public static void alternateFormInBlockInventoryContinuouslyDecaysAndHeatsTouchingSink(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(2, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
        ItemStack dust = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("chemlib:bismuth_dust")));
        chest.setItem(0, dust);
        chest.setChanged();
        NuclearSurfaceScanner.markActive(chest);
        LatentMachineBlockEntity sink = placeMachine(helper, chestPos.east(), LatentChemlibMod.GAS_TANK.get());

        helper.succeedWhen(() -> {
            ItemStack stored = chest.getItem(0);
            helper.assertTrue(stored.hasTag() && stored.getOrCreateTag().contains(NuclearStackData.STATE_KEY),
                "Alternate form must carry its per-unit nuclear ledger");
            ChemicalState state = ChemicalState.load(stored.getOrCreateTag().getCompound(NuclearStackData.STATE_KEY));
            helper.assertTrue(state.massOf("chemlib:thallium") > 0.0, "Bismuth dust must continuously form its daughter");
            helper.assertTrue(sink.getHeat() >= 700.0f, "Bi-209 dust must deliver substantial configured decay heat to touching HeatSync material");
            helper.assertTrue(NuclearStackData.isotopes(stored).fraction(209) == 1.0, "Isotope state must remain bound to the material ledger");
            helper.assertTrue(state.isotopesOf("chemlib:bismuth").fraction(209) == 1.0,
                "Partial parent matter must remain explicitly Bi-209");
            helper.assertTrue(state.isotopesOf("chemlib:thallium").fraction(205) == 1.0,
                "Daughter matter must be explicitly Tl-205 rather than inheriting Bi-209");
            helper.assertTrue(NuclearStackData.provenance(stored).equals("chemlib:bismuth_dust"), "Alternate-form provenance must survive daughter formation");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 80)
    public static void sealedCellInBlockInventoryUsesTheSameContinuousDecayPath(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(2, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
        ItemStack cell = new ItemStack(LatentChemlibMod.SEALED_CHEMICAL_CELL.get());
        ChemicalCellItem.setState(cell, new ChemicalState("chemlib:bismuth", 1_000.0, 8.0, 600.0, 0.0, 0.0));
        chest.setItem(0, cell);
        chest.setChanged();
        NuclearSurfaceScanner.markActive(chest);
        LatentMachineBlockEntity sink = placeMachine(helper, chestPos.east(), LatentChemlibMod.GAS_TANK.get());

        helper.succeedWhen(() -> {
            ChemicalState state = ChemicalCellItem.state(chest.getItem(0));
            helper.assertTrue(state.massOf("chemlib:thallium") > 0.0, "Stored sealed cell must form its daughter");
            helper.assertTrue(sink.getHeat() >= 3_500.0f, "Stored sealed cell must deliver substantial configured heat to touching HeatSync material");
            helper.assertTrue(NuclearStackData.isotopes(chest.getItem(0)).fraction(209) == 1.0,
                "Sealed-cell isotope identity must survive daughter formation");
            helper.assertTrue(state.isotopesOf("chemlib:bismuth").fraction(209) == 1.0,
                "Cell parent matter must remain explicitly Bi-209");
            helper.assertTrue(state.isotopesOf("chemlib:thallium").fraction(205) == 1.0,
                "Cell daughter matter must be explicitly Tl-205");
            helper.assertTrue(NuclearStackData.provenance(chest.getItem(0)).equals("sealed_cell:chemlib:bismuth"),
                "Sealed-cell provenance must survive daughter formation");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 80)
    public static void droppedAlternateFormContinuouslyDecaysAndHeatsTouchingSink(GameTestHelper helper) {
        BlockPos itemPos = new BlockPos(2, 1, 1);
        ItemStack dust = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("chemlib:bismuth_dust")));
        ItemEntity item = helper.spawnItem(dust.getItem(), itemPos);
        item.setNoGravity(true);
        LatentMachineBlockEntity sink = placeMachine(helper, itemPos.east(), LatentChemlibMod.GAS_TANK.get());

        helper.succeedWhen(() -> {
            helper.assertTrue(item.isAlive(), "Dropped radioactive alternate form must remain represented after continuous decay");
            ItemStack stored = item.getItem();
            helper.assertTrue(stored.hasTag() && stored.getOrCreateTag().contains(NuclearStackData.STATE_KEY),
                "Dropped alternate form must carry its nuclear ledger");
            ChemicalState state = ChemicalState.load(stored.getOrCreateTag().getCompound(NuclearStackData.STATE_KEY));
            helper.assertTrue(state.massOf("chemlib:thallium") > 0.0, "Dropped Bi-209 dust must form its daughter");
            helper.assertTrue(sink.getHeat() >= 700.0f, "Dropped Bi-209 dust must heat touching HeatSync material substantially");
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearPhenomena", timeoutTicks = 100)
    public static void carriedAlternateFormContinuouslyDecays(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(
            helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "nuclear-holder-probe")
        );
        BlockPos playerPos = helper.absolutePos(new BlockPos(2, 1, 1));
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        ItemStack plate = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("chemlib:bismuth_plate")));
        player.getInventory().setItem(0, plate);
        player.getInventory().setChanged();
        NuclearSurfaceScanner.INSTANCE.scanPlayerNow(helper.getLevel(), player);

        ItemStack carried = player.getInventory().getItem(0);
        helper.assertTrue(carried.hasTag() && carried.getOrCreateTag().contains(NuclearStackData.STATE_KEY),
            "Carried radioactive alternate form must be processed by the player inventory scanner");
        ChemicalState state = ChemicalState.load(carried.getOrCreateTag().getCompound(NuclearStackData.STATE_KEY));
        helper.assertTrue(state.massOf("chemlib:thallium") > 0.0, "Carried Bi-209 plate must continuously form its daughter");
        helper.assertTrue(state.energy() >= 1_500.0, "Two seconds of unaccepted carried decay heat must remain in the material state");
        helper.assertTrue(NuclearStackData.provenance(carried).equals("chemlib:bismuth_plate"),
            "Carried form provenance must survive daughter formation");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearAtomicity", timeoutTicks = 40)
    public static void exhaustedConsequenceBudgetLeavesStackNbtByteForByteUnchanged(GameTestHelper helper) {
        ItemStack cell = new ItemStack(LatentChemlibMod.SEALED_CHEMICAL_CELL.get());
        ChemicalCellItem.setState(cell, new ChemicalState("chemlib:bismuth", 1_000.0, 8.0, 600.0, 0.0, 0.0));
        CompoundTag before = cell.getTag().copy();
        while (SimulationScheduler.INSTANCE.trySpend(helper.getLevel(), SimulationBudget.NUCLEAR_MUTATIONS, 1)) {
            // Exhaust this transaction resource without touching the test stack.
        }
        var status = NuclearSimulationService.INSTANCE.processStack(
            helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)), cell, 1.0,
            NuclearSimulationService.NuclearEnvironment.EMPTY, null, ignored -> {}
        );
        helper.assertTrue(status == NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED,
            "A continuous decay transaction must stop when any consequence budget is unavailable");
        helper.assertTrue(before.equals(cell.getTag()),
            "Failed reservation must leave state, isotope, provenance, and exposure NBT byte-for-byte unchanged");

        ItemStack raw = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("chemlib:bismuth")));
        helper.assertTrue(raw.getTag() == null, "Evaluation purity probe must start without NBT");
        NuclearSimulationService.INSTANCE.evaluateStack(
            raw, 1.0, NuclearSimulationService.NuclearEnvironment.EMPTY, RandomSource.create(42L)
        );
        helper.assertTrue(raw.getTag() == null, "Public stack evaluation must not advance exposure or initialize nuclear NBT");
        // Keep this batch alive through the next scheduler reset so its deliberate exhaustion cannot starve later batches.
        helper.runAfterDelay(21, helper::succeed);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "placedNuclear", timeoutTicks = 40)
    public static void exactForeignRadioactiveBlocksAndRadonLampCreatePersistentEntries(GameTestHelper helper) {
        List<String> blockIds = List.of(
            "chemlib:actinium_metal_block", "chemlib:bismuth_metal_block",
            "chemlib:francium_metal_block", "chemlib:polonium_metal_block",
            "chemlib:protactinium_metal_block", "chemlib:radium_metal_block",
            "chemlib:thorium_metal_block", "chemlib:uranium_metal_block",
            "chemlib:radon_lamp_block"
        );
        for (int index = 0; index < blockIds.size(); index++) {
            String blockId = blockIds.get(index);
            Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(blockId));
            helper.assertTrue(block != null && block != Blocks.AIR, "Placed nuclear registration must exist: " + blockId);
            BlockPos relative = new BlockPos(1 + index, 1, 1);
            helper.setBlock(relative, block);
            BlockPos absolute = helper.absolutePos(relative);
            PlacedNuclearData.Entry entry = PlacedNuclearLifecycle.trackPlaced(
                helper.getLevel(), absolute, new ItemStack(block.asItem())
            ).orElseThrow(() -> new AssertionError("Placed nuclear form must resolve: " + blockId));
            helper.assertTrue(entry.state().mass() > 0.0, "Placed entry must contain conserved matter: " + blockId);
            helper.assertTrue(entry.state().isotopesOf(entry.state().chemicalId()).fraction(entry.isotopeMassNumber()) > 0.0,
                "Placed entry must initialize canonical isotope identity: " + blockId);
        }
        PlacedNuclearData.Entry lamp = PlacedNuclearData.get(helper.getLevel())
            .get(helper.absolutePos(new BlockPos(9, 1, 1))).orElseThrow();
        helper.assertTrue(lamp.materialUnits() == 5.0, "Radon lamp must contain its five recipe radon units");
        for (int index = 0; index < blockIds.size(); index++) {
            BlockPos absolute = helper.absolutePos(new BlockPos(1 + index, 1, 1));
            PlacedNuclearData.get(helper.getLevel()).remove(absolute);
            NuclearSurfaceScanner.unmarkPlaced(helper.getLevel(), absolute);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "placedNuclear", timeoutTicks = 80)
    public static void placedRadioactiveBlockHeatsTouchingHeatSyncMaterialAndRetainsDaughters(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 1, 2);
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("chemlib:bismuth_metal_block"));
        helper.setBlock(sourcePos, block);
        PlacedNuclearLifecycle.trackPlaced(
            helper.getLevel(), helper.absolutePos(sourcePos), new ItemStack(block.asItem())
        ).orElseThrow();
        LatentMachineBlockEntity sink = placeMachine(helper, sourcePos.east(), LatentChemlibMod.GAS_TANK.get());

        helper.runAfterDelay(20, () -> {
            NuclearSurfaceScanner.INSTANCE.scanPlacedNow(helper.getLevel(), helper.absolutePos(sourcePos));
            PlacedNuclearData.Entry entry = PlacedNuclearData.get(helper.getLevel())
                .get(helper.absolutePos(sourcePos)).orElseThrow();
            helper.assertTrue(sink.getHeat() > 0.0f, "Placed Bi-209 must deliver substantial decay heat to touching HeatSync material");
            helper.assertTrue(entry.state().isotopesOf("chemlib:bismuth").fraction(209) == 1.0,
                "Remaining placed parent must stay Bi-209");
            helper.assertTrue(entry.state().isotopesOf("chemlib:thallium").fraction(205) == 1.0,
                "Placed daughter must be explicit Tl-205");
            PlacedNuclearData.get(helper.getLevel()).remove(helper.absolutePos(sourcePos));
            NuclearSurfaceScanner.unmarkPlaced(helper.getLevel(), helper.absolutePos(sourcePos));
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "placedNuclear", timeoutTicks = 60)
    public static void radioactiveRadonBucketEscapesIntoIdentityPreservingCloud(GameTestHelper helper) {
        ItemStack radonBucket = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("chemlib:radon_bucket")));
        var form = RadioactiveFormResolver.INSTANCE.resolve(radonBucket).orElseThrow();
        ChemicalState radon = NuclearStackData.state(radonBucket, form).withPureIsotope("chemlib:radon", 222);
        NuclearStackData.setState(radonBucket, radon);
        NuclearStackData.bindIdentity(radonBucket, "gametest:radon_bucket", 222);
        radonBucket.getOrCreateTag().putString(NuclearStackData.PROVENANCE_KEY, "gametest:radon_bucket");
        CompoundTag clock = radonBucket.getOrCreateTag();
        com.gerald.latentchemlib.sim.LoadedExposureClock.commit(
            clock, new com.gerald.latentchemlib.sim.LoadedExposureClock.Window(40L, 40L, 99L)
        );
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));

        ItemStack replacement = GasEscapeHandler.INSTANCE.escapeStackNow(radonBucket, helper.getLevel(), origin);
        helper.assertTrue(replacement.is(Items.BUCKET), "Escaped radon bucket must return exactly one empty bucket");
        ChemicalCloudBlockEntity cloud = null;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3))) {
            if (helper.getLevel().getBlockEntity(candidate) instanceof ChemicalCloudBlockEntity found
                && found.chemicalState().massOf("chemlib:radon") > 0.0) {
                cloud = found;
                break;
            }
        }
        helper.assertTrue(cloud != null, "Radon bucket must become a native Latent gas cloud");
        helper.assertTrue(cloud.chemicalState().isotopesOf("chemlib:radon").fraction(222) == 1.0,
            "Radon cloud must preserve Rn-222 identity");
        helper.assertTrue(cloud.nuclearProvenance().equals("gametest:radon_bucket"),
            "Radon cloud must preserve bucket provenance");
        helper.assertTrue(cloud.loadedExposureTicks() == 40L,
            "Radon cloud must continue the bucket's loaded-exposure clock");
        helper.getLevel().removeBlock(cloud.getBlockPos(), false);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "placedNuclear", timeoutTicks = 80)
    public static void placedNuclearSaveReloadDoesNotReplayElapsedWindow(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(relative);
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("chemlib:bismuth_metal_block"));
        helper.setBlock(relative, block);
        PlacedNuclearLifecycle.trackPlaced(helper.getLevel(), pos, new ItemStack(block.asItem())).orElseThrow();

        helper.runAfterDelay(20, () -> {
            NuclearSurfaceScanner.INSTANCE.scanPlacedNow(helper.getLevel(), pos);
            PlacedNuclearData data = PlacedNuclearData.get(helper.getLevel());
            PlacedNuclearData.Entry before = data.get(pos).orElseThrow();
            PlacedNuclearData.Entry reloaded = PlacedNuclearData.load(data.save(new CompoundTag())).get(pos).orElseThrow();
            data.put(pos, reloaded);
            NuclearSurfaceScanner.INSTANCE.scanPlacedNow(helper.getLevel(), pos);
            PlacedNuclearData.Entry after = data.get(pos).orElseThrow();
            helper.assertTrue(after.loadedExposureTicks() == before.loadedExposureTicks(),
                "Reload at the same game time must not replay a processed exposure window");
            helper.assertTrue(after.state().equals(before.state()),
                "Reload at the same game time must not duplicate decay, heat, or daughter mass");
            data.remove(pos);
            NuclearSurfaceScanner.unmarkPlaced(helper.getLevel(), pos);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "placedNuclearTransfer", timeoutTicks = 80)
    public static void placedNuclearLootAndReplacementPreserveStateExactly(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(relative);
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("chemlib:bismuth_metal_block"));
        helper.setBlock(relative, block);
        PlacedNuclearLifecycle.trackPlaced(helper.getLevel(), pos, new ItemStack(block.asItem())).orElseThrow();

        helper.runAfterDelay(20, () -> {
            NuclearSurfaceScanner.INSTANCE.scanPlacedNow(helper.getLevel(), pos);
            PlacedNuclearData.Entry before = PlacedNuclearData.get(helper.getLevel()).get(pos).orElseThrow();
            List<ItemStack> drops = Block.getDrops(
                helper.getLevel().getBlockState(pos), helper.getLevel(), pos,
                helper.getLevel().getBlockEntity(pos), null, ItemStack.EMPTY
            );
            ItemStack preserved = drops.stream().filter(stack -> stack.is(block.asItem())).findFirst().orElseThrow();
            helper.assertTrue(preserved.hasTag() && preserved.getOrCreateTag().contains(NuclearStackData.STATE_KEY),
                "Global loot modifier must attach placed nuclear state to the native self-drop");
            helper.assertTrue(PlacedNuclearData.get(helper.getLevel()).get(pos).isEmpty(),
                "Loot transfer must atomically consume the position sidecar");

            helper.setBlock(relative, block);
            PlacedNuclearData.Entry replaced = PlacedNuclearLifecycle.trackPlaced(helper.getLevel(), pos, preserved).orElseThrow();
            helper.assertTrue(replaced.state().equals(before.state()),
                "Break and replacement must preserve mass, daughter identity, retained heat, and conditions exactly");
            helper.assertTrue(replaced.loadedExposureTicks() == before.loadedExposureTicks(),
                "Break and replacement must preserve the exactly-once loaded-exposure clock");
            PlacedNuclearData.get(helper.getLevel()).remove(pos);
            NuclearSurfaceScanner.unmarkPlaced(helper.getLevel(), pos);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = "nuclearDiscrete", timeoutTicks = 40)
    public static void discreteDaughterConsumesRejectedHeatAndAcceptedHeatIsNotDuplicated(GameTestHelper helper) {
        var bismuthItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("chemlib:bismuth"));
        var thalliumItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("chemlib:thallium"));
        NuclearSimulationService.NuclearStackEvent event = new NuclearSimulationService.NuclearStackEvent(
            "chemlib:thallium", thalliumItem, 1, 1, 120.0f, 1,
            NuclearSimulationService.NuclearEventType.DECAY, 209, 205
        );

        ItemStack rejectedInput = new ItemStack(bismuthItem, 2);
        List<ItemStack> rejectedOutput = new ArrayList<>();
        var rejectedStatus = NuclearSimulationService.INSTANCE.applyStackEvent(
            helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), rejectedInput,
            event, null, (java.util.function.Consumer<ItemStack>) rejectedOutput::add
        );
        helper.assertTrue(rejectedStatus == NuclearSimulationService.ProcessStatus.MUTATED && rejectedInput.getCount() == 1,
            "Discrete processing must consume exactly one unit from a larger stack");
        ItemStack daughter = rejectedOutput.get(0);
        ChemicalState daughterState = ChemicalState.load(daughter.getOrCreateTag().getCompound(NuclearStackData.STATE_KEY));
        helper.assertTrue(daughterState.energy() == 120.0,
            "Rejected discrete heat must be durably bound to the daughter state, not an unread side key");
        helper.assertTrue(daughterState.isotopesOf("chemlib:thallium").fraction(205) == 1.0,
            "Discrete daughter must carry explicit data-driven Tl-205 identity");

        LatentMachineBlockEntity sink = placeMachine(helper, new BlockPos(5, 1, 2), LatentChemlibMod.GAS_TANK.get());
        ItemStack acceptedInput = new ItemStack(bismuthItem);
        List<ItemStack> acceptedOutput = new ArrayList<>();
        NuclearSimulationService.INSTANCE.applyStackEvent(
            helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 2)), acceptedInput,
            event, sink, (java.util.function.Consumer<ItemStack>) acceptedOutput::add
        );
        ChemicalState acceptedState = ChemicalState.load(
            acceptedOutput.get(0).getOrCreateTag().getCompound(NuclearStackData.STATE_KEY)
        );
        helper.assertTrue(sink.getHeat() == 120.0f && acceptedState.energy() == 0.0,
            "Accepted heat must enter HeatSync exactly once and must not also remain on the daughter");
        helper.succeed();
    }

    private static void assertMachineEntity(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        helper.assertTrue(helper.getBlockEntity(pos) instanceof LatentMachineBlockEntity, block.getDescriptionId() + " should create a latent machine entity");
    }

    private static void assertAdvancedChimneysRoute(GameTestHelper helper, AdvancedChimneysRoute route) {
        BlockPos emitterPos = new BlockPos(1, 1, 1);
        helper.setBlock(
            emitterPos,
            Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true)
        );
        BlockEntity emitterBlockEntity = helper.getBlockEntity(emitterPos);
        helper.assertTrue(emitterBlockEntity instanceof FurnaceBlockEntity, "The route fixture must use a live furnace block entity");

        BlockPos expectedOutlet = route.place(helper, emitterPos);
        BlockPos absoluteEmitterPos = helper.absolutePos(emitterPos);
        BlockPos absoluteOutlet = helper.absolutePos(expectedOutlet);

        var adpotherEmitter = AdPother.getInstance().emitters
            .get(helper.getLevel(), absoluteEmitterPos)
            .orElseThrow(() -> new AssertionError("AdPother must load the configured minecraft:furnace emitter"));
        helper.assertTrue(
            AdChimneys.getInstance().emitters.get(helper.getLevel(), absoluteEmitterPos).isPresent(),
            "Advanced Chimneys must load the same configured minecraft:furnace emitter"
        );

        Pollutant<?> carbon = AdPother.getInstance().pollutants.findByName("carbon")
            .orElseThrow(() -> new AssertionError("AdPother carbon selector must be registered"));
        List<AdpotherRoutingProbe.RouteEvent> routeEvents = new ArrayList<>();
        int accepted = AdpotherRoutingProbe.observe(
            routeEvents::add,
            () -> carbon.emitFrom(emitterBlockEntity, adpotherEmitter.getRelatedBlocks(), 1)
        );

        helper.assertTrue(accepted == 1, route.label + " must accept exactly one configured emitter unit");
        helper.assertTrue(
            routeEvents.size() == 1,
            route.label + " must select exactly one pollution outlet; observed " + routeEvents
        );
        AdpotherRoutingProbe.RouteEvent event = routeEvents.get(0);
        helper.assertTrue(
            event.outlet().equals(absoluteOutlet),
            route.label + " must hand off at its resolved native outlet; expected " + absoluteOutlet + " but observed " + event.outlet()
        );
        helper.assertTrue(event.requested() == 1, route.label + " filter stage must account for the one requested unit");
        helper.assertTrue(event.filtered() == 0, route.label + " empty filter path must not destroy the configured unit");
        helper.assertTrue(event.emitted() == 1, route.label + " outlet handoff must insert the remaining unit into Latent");

        BlockPos scanFrom = expectedOutlet.offset(-3, -3, -3);
        BlockPos scanTo = expectedOutlet.offset(3, 3, 3);
        helper.assertTrue(
            totalCloudChemicalMass(helper, scanFrom, scanTo, "chemlib:carbon_dioxide") == 16.0,
            route.label + " must convert one emitter unit into exactly 16 Latent carbon-dioxide mass"
        );
        helper.assertTrue(
            totalCloudMass(helper, scanFrom, scanTo) == 16.0,
            route.label + " must not duplicate or invent atmospheric mass"
        );
        for (BlockPos pos : BlockPos.betweenClosed(scanFrom, scanTo)) {
            helper.assertTrue(!helper.getBlockState(pos).is(carbon), route.label + " must not leave a legacy AdPother gas block");
        }
        helper.succeed();
    }

    private enum AdvancedChimneysRoute {
        CHIMNEY("Advanced Chimneys chimney") {
            @Override
            BlockPos place(GameTestHelper helper, BlockPos emitterPos) {
                BlockPos chimneyPos = emitterPos.above();
                helper.setBlock(chimneyPos, AdChimneys.getInstance().blocks.cobblestone_chimney.get());
                helper.assertTrue(helper.getBlockState(chimneyPos).getBlock() instanceof Chimney, "Chimney fixture must use the registered Advanced Chimneys block");
                return chimneyPos;
            }
        },
        VENT("Advanced Chimneys vent network") {
            @Override
            BlockPos place(GameTestHelper helper, BlockPos emitterPos) {
                BlockPos pumpPos = emitterPos.above();
                BlockPos firstVent = pumpPos.above();
                BlockPos secondVent = firstVent.east();
                helper.setBlock(firstVent.west(), Blocks.STONE);
                helper.setBlock(firstVent.north(), Blocks.STONE);
                helper.setBlock(firstVent.south(), Blocks.STONE);
                helper.setBlock(secondVent.north(), Blocks.STONE);
                helper.setBlock(secondVent.south(), Blocks.STONE);
                helper.setBlock(firstVent, AdChimneys.getInstance().blocks.stone_vent.get());
                helper.setBlock(secondVent, AdChimneys.getInstance().blocks.stone_vent.get());
                helper.setBlock(
                    pumpPos,
                    AdChimneys.getInstance().blocks.stone_pump.get().defaultBlockState()
                        .setValue(BlockStateProperties.LIT, true)
                );
                helper.assertTrue(helper.getBlockState(firstVent).getBlock() instanceof Vent, "Vent fixture must use the registered Advanced Chimneys block");
                helper.assertTrue(helper.getBlockState(secondVent).getBlock() instanceof Vent, "Vent fixture must use a live two-block vent network");
                // ForgeEndertech's VentPipe.Output resolves and debits the starting vent before
                // traversing the rest of the chain when Advanced Chimneys accepts every outlet.
                return firstVent;
            }
        },
        PUMP("Advanced Chimneys pump") {
            @Override
            BlockPos place(GameTestHelper helper, BlockPos emitterPos) {
                BlockPos pumpPos = emitterPos.above();
                helper.setBlock(
                    pumpPos,
                    AdChimneys.getInstance().blocks.stone_pump.get().defaultBlockState()
                        .setValue(BlockStateProperties.LIT, true)
                );
                helper.assertTrue(helper.getBlockState(pumpPos).getBlock() instanceof Pump, "Pump fixture must use the registered Advanced Chimneys block");
                return pumpPos.above();
            }
        },
        PIPE("Advanced Chimneys pipe") {
            @Override
            BlockPos place(GameTestHelper helper, BlockPos emitterPos) {
                BlockPos pipePos = emitterPos.above();
                helper.setBlock(pipePos, AdChimneys.getInstance().blocks.pipe.get());
                helper.assertTrue(helper.getBlockState(pipePos).getBlock() instanceof Pipe, "Pipe fixture must use the registered Advanced Chimneys block");
                return pipePos.above();
            }
        };

        private final String label;

        AdvancedChimneysRoute(String label) {
            this.label = label;
        }

        abstract BlockPos place(GameTestHelper helper, BlockPos emitterPos);
    }

    private static ChemicalCloudBlockEntity placeCloud(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get());
        return cloudAt(helper, pos);
    }

    private static ChemicalCloudBlockEntity cloudAt(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        if (blockEntity instanceof ChemicalCloudBlockEntity cloud) return cloud;
        throw new IllegalStateException("Expected chemical cloud at " + pos);
    }

    private static LatentMachineBlockEntity placeMachine(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        if (blockEntity instanceof LatentMachineBlockEntity machine) return machine;
        throw new IllegalStateException("Expected latent machine at " + pos);
    }

    private static IFluidHandler fluidHandler(LatentMachineBlockEntity machine) {
        return machine.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(AssertionError::new);
    }

    private static double totalCloudMass(GameTestHelper helper, BlockPos from, BlockPos to) {
        double mass = 0.0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof ChemicalCloudBlockEntity cloud) {
                mass += cloud.chemicalState().mass();
            }
        }
        return mass;
    }

    private static double totalCloudChemicalMass(GameTestHelper helper, BlockPos from, BlockPos to, String chemicalId) {
        double mass = 0.0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof ChemicalCloudBlockEntity cloud) {
                mass += cloud.chemicalState().massOf(chemicalId);
            }
        }
        return mass;
    }

    private static int totalCloudCount(GameTestHelper helper, BlockPos from, BlockPos to) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (helper.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity) {
                count++;
            }
        }
        return count;
    }
}
