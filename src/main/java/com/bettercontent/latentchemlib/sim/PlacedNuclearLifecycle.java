package com.bettercontent.latentchemlib.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

/** Forge lifecycle boundary for native ChemLib blocks whose implementation cannot store nuclear NBT. */
public final class PlacedNuclearLifecycle {
    public static final PlacedNuclearLifecycle INSTANCE = new PlacedNuclearLifecycle();

    private PlacedNuclearLifecycle() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.isCanceled()) return;
        Optional<PlacedNuclearResolver.ResolvedPlacement> resolved =
            PlacedNuclearResolver.INSTANCE.resolve(event.getPlacedBlock());
        if (resolved.isEmpty()) return;
        ItemStack source = event.getEntity() instanceof Player player
            ? matchingHandStack(player, resolved.get().form().formId())
            : ItemStack.EMPTY;
        trackPlaced(level, event.getPos(), source);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFillBucket(FillBucketEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getTarget() instanceof BlockHitResult hit)
            || !event.getEmptyBucket().is(Items.BUCKET)) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Optional<PlacedNuclearResolver.ResolvedPlacement> resolved = PlacedNuclearResolver.INSTANCE.resolve(state);
        if (resolved.isEmpty() || !resolved.get().nativePhase() || !(state.getBlock() instanceof BucketPickup pickup)) return;

        PlacedNuclearData data = PlacedNuclearData.get(level);
        PlacedNuclearData.Entry entry = data.get(pos).orElseGet(() ->
            data.initialize(pos, state, ItemStack.EMPTY, level.getGameTime(), level.getSeed() ^ pos.asLong()).orElseThrow()
        );
        ItemStack filled = pickup.pickupBlock(level, pos, state);
        if (filled.isEmpty()) return;
        ItemStack preserved = entry.toStack();
        if (!preserved.isEmpty()) filled.setTag(preserved.getTag() == null ? null : preserved.getTag().copy());
        pickup.getPickupSound(state).ifPresent(sound ->
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f)
        );
        event.getEntity().awardStat(Stats.ITEM_USED.get(event.getEmptyBucket().getItem()));
        data.remove(pos);
        NuclearSurfaceScanner.unmarkPlaced(level, pos);
        event.setFilledBucket(filled);
        event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        PlacedNuclearData data = PlacedNuclearData.get(level);
        for (BlockPos pos : data.positionsInChunk(chunk.getPos())) {
            if (PlacedNuclearResolver.INSTANCE.matches(chunk.getBlockState(pos), data.get(pos).orElseThrow())) {
                data.touch(pos, level.getGameTime());
                NuclearSurfaceScanner.markPlacedActive(level, pos);
            } else {
                data.remove(pos);
            }
        }
        reconcileChunk(level, chunk, data);
    }

    public static Optional<PlacedNuclearData.Entry> trackPlaced(ServerLevel level, BlockPos pos, ItemStack source) {
        PlacedNuclearData data = PlacedNuclearData.get(level);
        Optional<PlacedNuclearData.Entry> entry = data.initialize(
            pos, level.getBlockState(pos), source, level.getGameTime(), level.getSeed() ^ pos.asLong()
        );
        entry.ifPresent(ignored -> NuclearSurfaceScanner.markPlacedActive(level, pos));
        return entry;
    }

    private static ItemStack matchingHandStack(Player player, String formId) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem());
            if (key != null && key.toString().equals(formId)) return held;
        }
        return ItemStack.EMPTY;
    }

    private static void reconcileChunk(ServerLevel level, LevelChunk chunk, PlacedNuclearData data) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> PlacedNuclearResolver.INSTANCE.resolve(state).isPresent())) continue;
            int minY = level.getSectionYFromSectionIndex(sectionIndex) << 4;
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (PlacedNuclearResolver.INSTANCE.resolve(state).isEmpty()) continue;
                        BlockPos pos = new BlockPos(minX + localX, minY + localY, minZ + localZ);
                        if (data.get(pos).isEmpty()) {
                            data.initialize(pos, state, ItemStack.EMPTY, level.getGameTime(), level.getSeed() ^ pos.asLong());
                        }
                        NuclearSurfaceScanner.markPlacedActive(level, pos);
                    }
                }
            }
        }
    }
}
