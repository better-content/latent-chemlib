package com.bettercontent.latentchemlib.gametest;

import com.bettercontent.latentchemlib.api.IsotopeEnsemble;
import com.bettercontent.latentchemlib.api.IsotopeItemData;
import com.bettercontent.latentchemlib.sim.NuclearStackData;
import com.bettercontent.latentchemlib.sim.PlacedNuclearData;
import com.bettercontent.latentchemlib.sim.PlacedNuclearResolver;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

@GameTestHolder("latent_chemlib")
@PrefixGameTestTemplate(false)
public final class PlacedNuclearGameTests {
    private static final ResourceLocation URANIUM = new ResourceLocation("chemlib", "uranium_metal_block");
    private static final IsotopeEnsemble ENRICHED = IsotopeEnsemble.pure(235, IsotopeEnsemble.Binding.PERMANENT);

    @GameTest(template = "empty")
    public static void player_placement_tracks_nuclear_state(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemStack source = placeEnrichedBlock(helper, pos);
        var entry = PlacedNuclearData.get(helper.getLevel()).get(pos).orElseThrow();
        helper.assertTrue(source.getCount() == 1, "Survival placement must consume exactly one of two blocks");
        helper.assertTrue(PlacedNuclearResolver.INSTANCE.matches(helper.getLevel().getBlockState(pos), entry),
            "Placement event must bind the actual world block to its matching material form");
        helper.assertTrue(entry.isotopes().equals(ENRICHED), "Placement must preserve the held isotope composition");
        helper.assertTrue(entry.materialUnits() == 9.0 && entry.state().mass() > 0,
            "A placed metal block must retain one block's material ledger");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collection_preserves_isotopes_and_consumes_sidecar(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        placeEnrichedBlock(helper, pos);
        var level = helper.getLevel();
        var data = PlacedNuclearData.get(level);
        var before = data.get(pos).orElseThrow();
        var state = level.getBlockState(pos);
        var drops = Block.getDrops(state, level, pos, null, null, new ItemStack(Items.NETHERITE_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 1
            && drops.get(0).is(state.getBlock().asItem()), "Native block loot must produce exactly one metal block");
        ItemStack drop = drops.get(0);
        helper.assertTrue(IsotopeItemData.explicit(drop).equals(ENRICHED),
            "Registered global loot modifier must copy the isotope identity to the actual self-drop");
        helper.assertTrue(drop.getOrCreateTag().getCompound(NuclearStackData.STATE_KEY).equals(before.state().save()),
            "Collection must preserve the exact nuclear material state");
        helper.assertTrue(data.get(pos).isEmpty(), "Collection must atomically consume the position sidecar");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var emptyDrops = Block.getDrops(level.getBlockState(pos), level, pos, null);
        helper.assertTrue(emptyDrops.isEmpty(), "Collecting the removed position again must produce no material");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chunk_reconciliation_retains_valid_and_removes_stale(GameTestHelper helper) {
        BlockPos valid = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos stale = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos untracked = helper.absolutePos(new BlockPos(3, 1, 1));
        placeEnrichedBlock(helper, valid);
        placeEnrichedBlock(helper, stale);
        var level = helper.getLevel();
        var data = PlacedNuclearData.get(level);
        var original = data.get(valid).orElseThrow();
        var chunks = java.util.stream.Stream.of(valid, stale, untracked).map(level::getChunkAt).distinct().toList();
        level.setBlockAndUpdate(stale, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(untracked, level.getBlockState(valid));
        // Exercise the registered Forge boundary using a real loaded chunk. This does not
        // claim a disk unload/reload: fixture tickets intentionally keep the chunk loaded.
        chunks.forEach(chunk -> MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false)));
        var retained = data.get(valid).orElseThrow();
        helper.assertTrue(retained.state().save().equals(original.state().save())
            && retained.isotopes().equals(ENRICHED), "Reconciliation must not reset enriched material in a valid block");
        helper.assertTrue(data.get(stale).isEmpty(), "Reconciliation must remove the ledger for a missing world block");
        helper.assertTrue(data.get(untracked).isPresent(), "Reconciliation must discover a native block without a ledger");
        var once = data.save(new CompoundTag());
        chunks.forEach(chunk -> MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false)));
        helper.assertTrue(data.save(new CompoundTag()).equals(once),
            "Repeated chunk reconciliation must not duplicate or change material records");
        var restored = PlacedNuclearData.load(once);
        helper.assertTrue(restored.get(valid).orElseThrow().state().save().equals(original.state().save())
            && restored.get(stale).isEmpty() && restored.get(untracked).isPresent(),
            "SavedData roundtrip must preserve the reconciled world ledger");
        helper.succeed();
    }

    private static ItemStack placeEnrichedBlock(GameTestHelper helper, BlockPos pos) {
        var block = ForgeRegistries.BLOCKS.getValue(URANIUM);
        helper.assertTrue(block != null && block != Blocks.AIR, "ChemLib uranium block must be registered");
        ItemStack stack = new ItemStack(block.asItem(), 2);
        // ChemLib's native BlockItem cannot expose Chemical; use the supported NBT boundary.
        stack.getOrCreateTag().put(IsotopeItemData.TAG_KEY, ENRICHED.save());
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "nuclear-test"));
        player.setPos(pos.getX() + 2.0, pos.getY(), pos.getZ() + 2.0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(result.consumesAction() && helper.getLevel().getBlockState(pos).is(block),
            "Native BlockItem placement must succeed through Forge's placement event path");
        helper.assertTrue(PlacedNuclearData.get(helper.getLevel()).get(pos).isPresent(),
            "Registered placement listener must create a nuclear sidecar");
        return stack;
    }
}
