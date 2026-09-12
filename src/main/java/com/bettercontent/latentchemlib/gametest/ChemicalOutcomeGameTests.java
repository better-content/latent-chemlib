package com.bettercontent.latentchemlib.gametest;

import com.bettercontent.heatsync.api.ThermalCapabilities;
import com.bettercontent.latentchemlib.api.event.ChemicalOutcomeEvent;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherGasBoundary;
import com.bettercontent.latentchemlib.sim.DisturbedRadioactiveData;
import com.bettercontent.latentchemlib.sim.LatentRadiationService;
import com.bettercontent.latentchemlib.sim.RadioactiveFormResolver;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("latent_chemlib")
@PrefixGameTestTemplate(false)
public final class ChemicalOutcomeGameTests {
    @GameTest(template = "chemical_empty")
    public static void gas_outcome_requires_actual_release(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 4, 4));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "gas-outcome"));
        player.setPos(Vec3.atCenterOf(origin));
        player.getInventory().clearContent();
        player.tickCount = 0;
        var gas = ForgeRegistries.ITEMS.getValue(new ResourceLocation("chemlib", "carbon_dioxide"));
        helper.assertTrue(gas != null, "Native carbon dioxide must exist");
        player.getInventory().setItem(0, new ItemStack(gas));
        var capture = new Capture(level, origin);
        MinecraftForge.EVENT_BUS.register(capture);
        try {
            // Every candidate native AdPother pump cell is occupied: the stack must remain.
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3)))
                level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(player.getInventory().getItem(0).is(gas) && capture.events.isEmpty(),
                "Blocked atmospheric release must retain material and publish no outcome");
            level.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            var pollutant = AdpotherGasBoundary.INSTANCE.pollutantFor("chemlib:carbon_dioxide").orElseThrow();
            helper.assertTrue(player.getInventory().getItem(0).isEmpty() && level.getBlockState(origin).is(pollutant),
                "Successful native release must consume gas and create the actual pollutant block");
            helper.assertTrue(capture.events.size() == 1
                && capture.events.get(0).kind() == ChemicalOutcomeEvent.Kind.GAS_RELEASED
                && player.getUUID().equals(capture.events.get(0).actor()),
                "Release outcome must be credited to the actual inventory owner");
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(capture.events.size() == 1, "Consumed gas cannot publish again next scan");
        } finally { MinecraftForge.EVENT_BUS.unregister(capture); }
        helper.succeed();
    }

    @GameTest(template = "chemical_empty")
    public static void radioactive_activation_requires_player_disturbance(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        var capture = new Capture(level, pos);
        MinecraftForge.EVENT_BUS.register(capture);
        try {
            // Diorite is bound only in this run's test-only tag resource, never in the mod JAR.
            level.setBlockAndUpdate(pos, Blocks.DIORITE.defaultBlockState());
            var form = RadioactiveFormResolver.INSTANCE.resolve(level.getBlockState(pos)).orElseThrow();
            helper.assertTrue(form.form().naturalWorldgenInert(), "Fixture must use the native inert hosted-ore rule");
            helper.assertTrue(DisturbedRadioactiveData.get(level).get(pos).isEmpty() && capture.events.isEmpty(),
                "An untouched world block must not become a player disturbance or a discovery");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ore-outcome"));
            player.setPos(pos.getX() + 2.0, pos.getY(), pos.getZ() + 2.0);
            ItemStack stack = new ItemStack(Blocks.DIORITE, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
            var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(result.consumesAction() && stack.getCount() == 1
                && DisturbedRadioactiveData.get(level).get(pos).isPresent(),
                "Committed native BlockItem placement must activate the disturbed-material ledger");
            helper.assertTrue(capture.events.size() == 1
                && capture.events.get(0).kind() == ChemicalOutcomeEvent.Kind.RADIOACTIVE_ACTIVATED
                && player.getUUID().equals(capture.events.get(0).actor()),
                "Only actual player placement publishes activation with its placing player");
        } finally { MinecraftForge.EVENT_BUS.unregister(capture); }
        helper.succeed();
    }

    @GameTest(template = "chemical_empty")
    public static void heat_outcome_requires_accepted_transfer(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 4, 4));
        for (Direction direction : Direction.values()) level.setBlockAndUpdate(pos.relative(direction), Blocks.AIR.defaultBlockState());
        var capture = new Capture(level, pos);
        MinecraftForge.EVENT_BUS.register(capture);
        try {
            LatentRadiationService.emit(level, pos, 1.0, 40.0);
            helper.assertTrue(capture.events.isEmpty(), "Emitting heat without an accepting thermal body is not useful transfer");
            var pipe = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("heat_sync", "heat_pipe"));
            helper.assertTrue(pipe != null && pipe != Blocks.AIR, "Native heat pipe must exist");
            level.setBlockAndUpdate(pos.east(), pipe.defaultBlockState());
            var body = level.getBlockEntity(pos.east()).getCapability(ThermalCapabilities.BODY, Direction.WEST).orElseThrow(IllegalStateException::new);
            double before = body.temperatureKelvin();
            LatentRadiationService.emit(level, pos, 1.0, 40.0);
            helper.assertTrue(body.temperatureKelvin() > before, "Actual heat body must become warmer");
            helper.assertTrue(capture.events.size() == 1
                && capture.events.get(0).kind() == ChemicalOutcomeEvent.Kind.HEAT_ACCEPTED
                && capture.events.get(0).amount() > 0 && capture.events.get(0).amount() <= 40,
                "Only accepted HU may publish useful heat outcome");
        } finally { MinecraftForge.EVENT_BUS.unregister(capture); }
        helper.succeed();
    }

    public static final class Capture {
        private final ServerLevel level;
        private final BlockPos pos;
        final List<ChemicalOutcomeEvent> events = new ArrayList<>();
        Capture(ServerLevel level, BlockPos pos) { this.level = level; this.pos = pos; }
        @SubscribeEvent public void outcome(ChemicalOutcomeEvent event) {
            if (event.level() == level && event.pos().equals(pos)) events.add(event);
        }
    }
}
