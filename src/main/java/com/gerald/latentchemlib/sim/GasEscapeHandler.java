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
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public class GasEscapeHandler {
    public static final GasEscapeHandler INSTANCE = new GasEscapeHandler();
    private static final double ESCAPED_MASS_PER_ITEM = 16.0;
    private static final double ESCAPED_DENSITY_PER_ITEM = 0.18;
    private static final double ESCAPED_MIN_DENSITY = 0.03;
    private static final double ESCAPED_ENERGY_PER_ITEM = 6.0;

    private final Map<ServerLevel, ActiveHolderSet<BlockPos>> activeBlockInventories = new WeakHashMap<>();
    private final Map<ServerLevel, ActiveHolderSet<UUID>> activeEntityInventories = new WeakHashMap<>();

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (entity instanceof ItemEntity itemEntity) {
            replaceEscapedStack(itemEntity.getItem(), level, itemEntity.blockPosition()).ifPresent(replacement -> {
                itemEntity.setItem(replacement);
                if (replacement.isEmpty()) itemEntity.discard();
            });
            if (itemEntity.isAlive() && escapePayload(itemEntity.getItem()).isPresent()) entityInventories(level).add(itemEntity.getUUID());
        } else if (entity instanceof Container || hasEscapableHolder(entity)) {
            entityInventories(level).add(entity.getUUID());
        }
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) entityInventories(level).remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onEquipmentChanged(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && escapePayload(event.getTo()).isPresent()) {
            entityInventories(level).add(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            gasifyLegacyFluidBlocks(level, chunk);
            for (BlockPos pos : chunk.getBlockEntities().keySet()) blockInventories(level).add(pos.immutable());
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var unloaded = event.getChunk().getPos();
            blockInventories(level).removeIf(pos -> new net.minecraft.world.level.ChunkPos(pos).equals(unloaded));
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            activeBlockInventories.remove(level);
            activeEntityInventories.remove(level);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.tickCount % 20 != 0
            || !(event.player instanceof net.minecraft.server.level.ServerPlayer player)
            || !(player.level() instanceof ServerLevel level)) return;
        scanContainer(player.getInventory(), player.blockPosition(), level);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
            || !(event.level instanceof ServerLevel level)
            || level.getGameTime() % 20L != 0L) {
            return;
        }
        blockInventories(level).visit(Integer.MAX_VALUE, pos -> {
            BlockEntity blockEntity = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
            if (blockEntity == null) return ActiveHolderSet.Decision.REMOVE;
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.ESCAPE_SCANS, 1)) return ActiveHolderSet.Decision.STOP;
            scanHolder(blockEntity, pos, level);
            return hasEscapableHolder(blockEntity) ? ActiveHolderSet.Decision.KEEP : ActiveHolderSet.Decision.REMOVE;
        });
        entityInventories(level).visit(Integer.MAX_VALUE, uuid -> {
            Entity entity = level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) return ActiveHolderSet.Decision.REMOVE;
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.ESCAPE_SCANS, 1)) return ActiveHolderSet.Decision.STOP;
            if (entity instanceof ItemEntity itemEntity) {
                replaceEscapedStack(itemEntity.getItem(), level, itemEntity.blockPosition())
                    .ifPresent(replacement -> {
                        itemEntity.setItem(replacement);
                        if (replacement.isEmpty()) itemEntity.discard();
                    });
                return itemEntity.isAlive() && escapePayload(itemEntity.getItem()).isPresent()
                    ? ActiveHolderSet.Decision.KEEP : ActiveHolderSet.Decision.REMOVE;
            } else {
                scanHolder(entity, entity.blockPosition(), level);
                // Container entities remain registered because their inventories can mutate without a block hook.
                return entity instanceof Container || hasEscapableHolder(entity)
                    ? ActiveHolderSet.Decision.KEEP : ActiveHolderSet.Decision.REMOVE;
            }
        });
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

    public static void markActive(BlockEntity blockEntity) {
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            if (!INSTANCE.hasEscapableHolder(blockEntity)) return;
            if (SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.ESCAPE_SCANS, 1)) {
                INSTANCE.scanHolder(blockEntity, blockEntity.getBlockPos(), level);
            }
            if (INSTANCE.hasEscapableHolder(blockEntity)) {
                INSTANCE.blockInventories(level).add(blockEntity.getBlockPos().immutable());
            }
        }
    }

    private boolean hasEscapableHolder(Object holder) {
        if (holder instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (escapePayload(container.getItem(slot)).isPresent()) return true;
            }
            return false;
        }
        if (holder instanceof BlockEntity blockEntity) {
            return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .map(this::hasEscapableStack)
                .orElse(false);
        }
        if (holder instanceof Entity entity) {
            return entity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .map(this::hasEscapableStack)
                .orElse(false);
        }
        return false;
    }

    private boolean hasEscapableStack(net.minecraftforge.items.IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (escapePayload(handler.getStackInSlot(slot)).isPresent()) return true;
        }
        return false;
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
        if (payload.isEmpty()) return Optional.empty();
        CloudInsertionService.InsertionResult inserted = CloudInsertionService.INSTANCE.insert(level, origin, payload.get().state());
        if (!inserted.acceptedAll()) return Optional.empty();
        if (inserted.target() != null && level.getBlockEntity(inserted.target()) instanceof ChemicalCloudBlockEntity cloud) {
            cloud.bindNuclearIdentity(payload.get().provenance(), payload.get().exposureTicks(), payload.get().seed());
        }
        return Optional.of(payload.get().replacement());
    }

    /** Deterministic live-server proof seam using the same atomic conversion path as inventory scans. */
    public ItemStack escapeStackNow(ItemStack stack, ServerLevel level, BlockPos origin) {
        return replaceEscapedStack(stack, level, origin).orElseGet(stack::copy);
    }

    private Optional<EscapePayload> escapePayload(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        Optional<RadioactiveFormResolver.ResolvedForm> radioactive = RadioactiveFormResolver.INSTANCE.resolve(stack);
        boolean gaseousContainer = stack.getItem() instanceof Chemical chemical && canEscapeAsGas(chemical)
            || stack.getItem() instanceof BucketItem bucket && GasFluidCodec.isGasFluid(bucket.getFluid());
        if (radioactive.isPresent() && gaseousContainer) {
            RadioactiveFormResolver.ResolvedForm form = radioactive.get();
            ChemicalState state = NuclearStackData.peekState(stack, form)
                .withMass(form.unitMass() * stack.getCount());
            LoadedExposureClock.Window exposure = LoadedExposureClock.preview(stack.getTag(), 0L, stack.hashCode());
            String provenance = NuclearStackData.provenance(stack);
            if (provenance.isBlank()) provenance = form.formId();
            ItemStack replacement = stack.getItem() instanceof BucketItem
                ? new ItemStack(Items.BUCKET, stack.getCount()) : ItemStack.EMPTY;
            return Optional.of(new EscapePayload(state, replacement, provenance, exposure.endTick(), exposure.seed()));
        }
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
                    new ItemStack(Items.BUCKET, stack.getCount()), "", 0L, 0L
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
        return CloudInsertionService.INSTANCE.insert(level, origin, state).acceptedAll();
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

    private ActiveHolderSet<BlockPos> blockInventories(ServerLevel level) {
        return activeBlockInventories.computeIfAbsent(level, ignored -> new ActiveHolderSet<>());
    }

    private ActiveHolderSet<UUID> entityInventories(ServerLevel level) {
        return activeEntityInventories.computeIfAbsent(level, ignored -> new ActiveHolderSet<>());
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

    private record EscapePayload(
        ChemicalState state, ItemStack replacement, String provenance, long exposureTicks, long seed
    ) {
        private EscapePayload(ChemicalState state, ItemStack replacement) {
            this(state, replacement, "", 0L, 0L);
        }
    }
}
