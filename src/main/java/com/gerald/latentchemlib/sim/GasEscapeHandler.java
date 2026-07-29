package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

public class GasEscapeHandler {
    public static final GasEscapeHandler INSTANCE = new GasEscapeHandler();
    private static final double ESCAPED_MASS_PER_ITEM = 16.0;
    private static final double ESCAPED_DENSITY_PER_ITEM = 0.18;
    private static final double ESCAPED_MIN_DENSITY = 0.03;
    private static final double ESCAPED_ENERGY_PER_ITEM = 6.0;

    private final Map<ServerLevel, Set<LevelChunk>> loadedChunks = new WeakHashMap<>();

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity itemEntity)) return;
        replaceEscapedStack(itemEntity.getItem(), (ServerLevel) event.getLevel(), itemEntity.blockPosition())
            .ifPresent(replacement -> {
                itemEntity.setItem(replacement);
                if (replacement.isEmpty()) itemEntity.discard();
            });
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            chunks(level).add(chunk);
            gasifyLegacyFluidBlocks(level, chunk);
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            chunks(level).remove(chunk);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) loadedChunks.remove(level);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
            || !(event.level instanceof ServerLevel level)
            || level.getGameTime() % 20L != 0L) {
            return;
        }
        for (LevelChunk chunk : Set.copyOf(chunks(level))) {
            for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                scanHolder(blockEntity, blockEntity.getBlockPos(), level);
            }
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                replaceEscapedStack(itemEntity.getItem(), level, itemEntity.blockPosition())
                    .ifPresent(replacement -> {
                        itemEntity.setItem(replacement);
                        if (replacement.isEmpty()) itemEntity.discard();
                    });
            } else if (entity instanceof Player player) {
                scanContainer(player.getInventory(), player.blockPosition(), level);
            } else {
                scanHolder(entity, entity.blockPosition(), level);
            }
        }
    }

    @SubscribeEvent
    public void onGasBucketUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos origin = event.getPos().relative(event.getFace());
        if (ventHeldGasBucket(event.getEntity(), event.getHand(), (ServerLevel) event.getLevel(), origin)) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGasBucketUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (ventHeldGasBucket(event.getEntity(), event.getHand(), (ServerLevel) event.getLevel(), event.getEntity().blockPosition())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFillGasBucket(FillBucketEvent event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof BlockHitResult hit)) return;
        if (GasFluidCodec.isGasFluid(event.getLevel().getFluidState(hit.getBlockPos()).getType())) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    private boolean ventHeldGasBucket(Player player, net.minecraft.world.InteractionHand hand, ServerLevel level, BlockPos origin) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BucketItem bucket) || !GasFluidCodec.isGasFluid(bucket.getFluid())) return false;
        Optional<ItemStack> replacement = replaceEscapedStack(held, level, origin);
        replacement.ifPresent(stack -> player.setItemInHand(hand, stack));
        return replacement.isPresent();
    }

    private void scanHolder(Object holder, BlockPos origin, ServerLevel level) {
        if (holder instanceof Container container) {
            scanContainer(container, origin, level);
            return;
        }
        if (holder instanceof BlockEntity blockEntity) {
            blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                if (handler instanceof IItemHandlerModifiable modifiable) scanItemHandler(modifiable, origin, level);
            });
        } else if (holder instanceof Entity entity) {
            entity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                if (handler instanceof IItemHandlerModifiable modifiable) scanItemHandler(modifiable, origin, level);
            });
        }
    }

    private void scanContainer(Container container, BlockPos origin, ServerLevel level) {
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            Optional<ItemStack> replacement = replaceEscapedStack(container.getItem(slot), level, origin);
            if (replacement.isPresent()) {
                container.setItem(slot, replacement.get());
                changed = true;
            }
        }
        if (changed) container.setChanged();
    }

    private void scanItemHandler(IItemHandlerModifiable handler, BlockPos origin, ServerLevel level) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            Optional<ItemStack> replacement = replaceEscapedStack(handler.getStackInSlot(slot), level, origin);
            if (replacement.isPresent()) handler.setStackInSlot(slot, replacement.get());
        }
    }

    private Optional<ItemStack> replaceEscapedStack(ItemStack stack, ServerLevel level, BlockPos origin) {
        Optional<EscapePayload> payload = escapePayload(stack);
        if (payload.isEmpty() || !spawnCloud(level, origin, payload.get().state())) return Optional.empty();
        return Optional.of(payload.get().replacement());
    }

    private Optional<EscapePayload> escapePayload(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        if (stack.getItem() instanceof Chemical chemical && canEscapeAsGas(chemical)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null) return Optional.empty();
            ChemicalTraits traits = LatentDataManager.INSTANCE.traits(id.toString());
            return Optional.of(new EscapePayload(
                escapedState(id.toString(), stack.getCount(), traits),
                ItemStack.EMPTY
            ));
        }
        if (stack.getItem() instanceof BucketItem bucket && GasFluidCodec.isGasFluid(bucket.getFluid())) {
            return GasFluidCodec.stateFromFluid(new net.minecraftforge.fluids.FluidStack(bucket.getFluid(), 1_000))
                .map(state -> new EscapePayload(
                    state.withMass(state.mass() * stack.getCount()),
                    new ItemStack(Items.BUCKET, stack.getCount())
                ));
        }
        return Optional.empty();
    }

    static ChemicalState escapedState(String chemicalId, int count, ChemicalTraits traits) {
        int stackCount = Math.max(0, count);
        return new ChemicalState(
            chemicalId,
            stackCount * ESCAPED_MASS_PER_ITEM,
            Math.max(ESCAPED_MIN_DENSITY, stackCount * traits.volatility() * ESCAPED_DENSITY_PER_ITEM),
            293.0,
            0.0,
            stackCount * ESCAPED_ENERGY_PER_ITEM
        );
    }

    public static boolean canEscapeAsGas(Chemical chemical) {
        return chemical != null && chemical.getMatterState() == MatterState.GAS;
    }

    /**
     * Seeds all matter into one viable cloud. The caller may consume its source only after true.
     */
    public static boolean spawnCloud(ServerLevel level, BlockPos origin, ChemicalState state) {
        if (state.mass() <= 0.0) return false;
        for (int radius = 0; radius <= 2; radius++) {
            for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius)
            )) {
                if (!level.isInWorldBounds(pos)) continue;
                if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity existing) {
                    ChemicalState current = existing.chemicalState();
                    if (current.mass() > 0.0 && !current.chemicalId().equals(state.chemicalId())) continue;
                    existing.seed(state);
                    return true;
                }
                if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) continue;
                if (!level.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3)) continue;
                if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud) {
                    cloud.seed(state);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces a gas-fluid block with its cloud representation at the same position.
     * A failed block replacement leaves the fluid and therefore its matter intact.
     */
    public static boolean gasifyFluidBlock(ServerLevel level, BlockPos pos) {
        var fluidState = level.getFluidState(pos);
        if (!GasFluidCodec.isGasFluid(fluidState.getType())) return false;
        int amount = Math.max(1, fluidState.getAmount()) * 125;
        Optional<ChemicalState> decoded = GasFluidCodec.stateFromFluid(
            new net.minecraftforge.fluids.FluidStack(fluidState.getType(), amount)
        );
        if (decoded.isEmpty()) return false;
        var cloudState = LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState();
        if (!level.setBlock(pos, cloudState, 3)) return false;
        ChemicalCloudBlockEntity cloud;
        if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity existing) {
            cloud = existing;
        } else {
            cloud = new ChemicalCloudBlockEntity(pos, cloudState);
            level.setBlockEntity(cloud);
        }
        cloud.seed(decoded.get());
        return true;
    }

    private Set<LevelChunk> chunks(ServerLevel level) {
        return loadedChunks.computeIfAbsent(
            level,
            ignored -> Collections.newSetFromMap(new IdentityHashMap<>())
        );
    }

    private void gasifyLegacyFluidBlocks(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> gasBlocks = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> GasFluidCodec.isGasFluid(state.getFluidState().getType()))) continue;
            int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
            int minY = sectionY << 4;
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (GasFluidCodec.isGasFluid(section.getFluidState(localX, localY, localZ).getType())) {
                            gasBlocks.add(new BlockPos(minX + localX, minY + localY, minZ + localZ));
                        }
                    }
                }
            }
        }
        for (BlockPos pos : gasBlocks) {
            gasifyFluidBlock(level, pos);
        }
    }

    private record EscapePayload(ChemicalState state, ItemStack replacement) {}
}
