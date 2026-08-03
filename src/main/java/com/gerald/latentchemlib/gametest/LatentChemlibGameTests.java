package com.gerald.latentchemlib.gametest;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.api.LatentCapabilities;
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
import com.gerald.latentchemlib.sim.NuclearPhenomenaMath;
import com.gerald.latentchemlib.data.LatentDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

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
