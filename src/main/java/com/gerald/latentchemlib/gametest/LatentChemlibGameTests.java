package com.gerald.latentchemlib.gametest;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.endertech.minecraft.mods.adpother.AdPother;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.gerald.latentchemlib.blockentity.LatentMachineBlockEntity;
import com.gerald.latentchemlib.item.ChemicalCellItem;
import com.gerald.latentchemlib.integration.adpother.AdpotherCloudView;
import com.gerald.latentchemlib.integration.adpother.LatentGasHazardService;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.GasFluidCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

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
        helper.succeed();
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
    public static void chemicalCloudSeedMergeExtractAndRejectsDifferentChemicals(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChemicalCloudBlockEntity cloud = placeCloud(helper, pos);
        cloud.seed(new ChemicalState("chemlib:hydrogen", 100.0, 1.0, 400.0, 0.2, 20.0));
        cloud.seed(new ChemicalState("chemlib:hydrogen", 300.0, 3.0, 800.0, 0.6, 60.0));

        ChemicalState merged = cloud.chemicalState();
        helper.assertTrue(merged.mass() == 400.0, "Matching chemical clouds should merge mass");
        helper.assertTrue(merged.temperature() == 700.0, "Merged cloud should weight temperature by mass");

        cloud.seed(new ChemicalState("chemlib:helium", 1_000.0, 10.0, 100.0, 0.0, 0.0));
        helper.assertTrue(cloud.chemicalState().chemicalId().equals("chemlib:hydrogen"), "Different chemicals should not overwrite an occupied cloud");
        helper.assertTrue(cloud.chemicalState().mass() == 400.0, "Rejected chemicals should not alter mass");

        ChemicalState extracted = cloud.extractMass(150.0);
        helper.assertTrue(extracted.mass() == 150.0, "Extracted cloud state should cap to requested mass");
        helper.assertTrue(cloud.chemicalState().mass() == 250.0, "Cloud should retain remaining mass after extraction");
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
    public static void gasCaptureRejectsAdjacentCloudsWithDifferentChemicals(GameTestHelper helper) {
        BlockPos capturePos = new BlockPos(1, 1, 1);
        BlockPos cloudPos = new BlockPos(2, 1, 1);
        LatentMachineBlockEntity capture = placeMachine(helper, capturePos, LatentChemlibMod.GAS_CAPTURE.get());
        capture.setStoredState(new ChemicalState("chemlib:helium", 200.0, 1.0, 300.0, 0.0, 20.0));
        ChemicalCloudBlockEntity cloud = placeCloud(helper, cloudPos);
        cloud.seed(new ChemicalState("chemlib:hydrogen", 800.0, 4.0, 600.0, 0.4, 120.0));

        helper.runAfterDelay(21, () -> {
            helper.assertTrue(capture.storedState().chemicalId().equals("chemlib:helium"), "Gas capture should keep its existing chemical when nearby clouds differ");
            helper.assertTrue(capture.storedState().mass() == 200.0, "Gas capture should not absorb incompatible clouds");
            helper.assertTrue(cloud.chemicalState().chemicalId().equals("chemlib:hydrogen"), "Rejected clouds should keep their chemical identity");
            helper.assertTrue(totalCloudMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4)) > 650.0,
                    "Rejected cloud mass should remain mostly conserved across nearby diffusion");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void gasReleaseDoesNotOverwriteDifferentChemicalCloudAbove(GameTestHelper helper) {
        BlockPos releasePos = new BlockPos(1, 1, 1);
        BlockPos cloudPos = releasePos.above();
        LatentMachineBlockEntity release = placeMachine(helper, releasePos, LatentChemlibMod.GAS_RELEASE.get());
        ChemicalCloudBlockEntity cloud = placeCloud(helper, cloudPos);
        release.setStoredState(new ChemicalState("chemlib:helium", 300.0, 2.0, 500.0, 0.1, 120.0));
        cloud.seed(new ChemicalState("chemlib:hydrogen", 400.0, 4.0, 650.0, 0.3, 160.0));

        helper.runAfterDelay(21, () -> {
            helper.assertTrue(cloud.chemicalState().chemicalId().equals("chemlib:hydrogen"), "Gas release should not overwrite an occupied cloud with a different chemical");
            helper.assertTrue(totalCloudMass(helper, new BlockPos(0, 0, 0), new BlockPos(4, 4, 4)) > 300.0,
                    "Occupied mismatched cloud mass should remain mostly conserved across nearby diffusion");
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

    private static void assertMachineEntity(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        helper.assertTrue(helper.getBlockEntity(pos) instanceof LatentMachineBlockEntity, block.getDescriptionId() + " should create a latent machine entity");
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
