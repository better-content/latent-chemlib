package com.bettercontent.latentchemlib.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public class NuclearSurfaceScanner {
    public static final NuclearSurfaceScanner INSTANCE = new NuclearSurfaceScanner();
    private static final int PLAYER_PERIOD_TICKS = 40;
    private static final int SURFACE_CLASSES = 4;

    private final Map<ServerLevel, ActiveHolderSet<UUID>> activePlayers = new WeakHashMap<>();
    private final Map<ServerLevel, ActiveHolderSet<UUID>> activeDroppedItems = new WeakHashMap<>();
    private final Map<ServerLevel, ActiveHolderSet<BlockPos>> activeBlockInventories = new WeakHashMap<>();
    private final Map<ServerLevel, ActiveHolderSet<BlockPos>> activePlacedMaterials = new WeakHashMap<>();
    private final Map<UUID, Integer> playerSlotCursors = new java.util.HashMap<>();
    private final Map<ServerLevel, Map<BlockPos, Integer>> blockInventorySlotCursors = new WeakHashMap<>();
    private final Map<ServerLevel, Integer> surfaceClassCursors = new WeakHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
            || !(player.level() instanceof ServerLevel level)) return;
        players(level).add(player.getUUID());
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || event.level.getGameTime() % 20L != 0L) return;
        if (!(event.level instanceof ServerLevel level)) return;
        level.players().forEach(player -> players(level).add(player.getUUID()));
        scanFairSurfaceRound(level);
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemEntity item)) return;
        if (isRelevant(item.getItem())) droppedItems(level).add(item.getUUID());
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity item) {
            droppedItems(level).remove(item.getUUID());
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (BlockPos pos : chunk.getBlockEntities().keySet()) blockInventories(level).add(pos.immutable());
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ChunkPos unloaded = event.getChunk().getPos();
        blockInventories(level).removeIf(pos -> new ChunkPos(pos).equals(unloaded));
        placedMaterials(level).removeIf(pos -> new ChunkPos(pos).equals(unloaded));
        blockInventorySlotCursors(level).keySet().removeIf(pos -> new ChunkPos(pos).equals(unloaded));
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        activeDroppedItems.remove(level);
        activeBlockInventories.remove(level);
        activePlacedMaterials.remove(level);
        activePlayers.remove(level);
        blockInventorySlotCursors.remove(level);
        surfaceClassCursors.remove(level);
        level.players().forEach(player -> playerSlotCursors.remove(player.getUUID()));
    }

    /** Called by the mutation hook; capability inspection is deferred to avoid chunk-load recursion. */
    public static void markActive(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) return;
        INSTANCE.blockInventories(level).add(blockEntity.getBlockPos().immutable());
    }

    public static void markPlacedActive(ServerLevel level, BlockPos pos) {
        INSTANCE.placedMaterials(level).add(pos.immutable());
    }

    public static void unmarkPlaced(ServerLevel level, BlockPos pos) {
        INSTANCE.placedMaterials(level).remove(pos);
    }

    static int advanceCursor(int cursor, int size, int advanced) {
        if (size <= 0) return 0;
        int next = cursor + Math.max(0, advanced);
        return next % size;
    }

    static int slotAt(int cursor, int size, int offset) {
        return size <= 0 ? 0 : Math.floorMod(cursor + offset, size);
    }

    static int surfaceClassAt(int cursor, int attempt) {
        return Math.floorMod(cursor + attempt, SURFACE_CLASSES);
    }

    static int surfaceClassAfter(int visitedClass) {
        return Math.floorMod(visitedClass + 1, SURFACE_CLASSES);
    }

    private void scanPlayerInventory(ServerLevel level, ServerPlayer player) {
        Inventory inventory = player.getInventory();
        NuclearSimulationService.NuclearEnvironment baseEnvironment = NuclearSimulationService.environment(level, player.blockPosition());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            NuclearSimulationService.NuclearEnvironment environment = inventoryEnvironment(inventory, slot, baseEnvironment);
            if (!NuclearSimulationService.INSTANCE.canProcessStack(stack, environment)) continue;
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) return;
            NuclearSimulationService.ProcessStatus status = processPlayerStack(level, player, inventory, slot, stack, environment);
            if (status == NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED) return;
        }
    }

    /** Deterministic entry point used by live-server probes without requiring a network connection. */
    public void scanPlayerNow(ServerLevel level, ServerPlayer player) {
        scanPlayerInventory(level, player);
    }

    private void scanFairSurfaceRound(ServerLevel level) {
        int holders = players(level).size() + droppedItems(level).size()
            + blockInventories(level).size() + placedMaterials(level).size();
        if (holders <= 0) return;
        int classCursor = Math.floorMod(surfaceClassCursors.getOrDefault(level, 0), SURFACE_CLASSES);
        int attempts = Math.max(SURFACE_CLASSES, holders * SURFACE_CLASSES);
        for (int attempt = 0; attempt < attempts; attempt++) {
            int holderClass = surfaceClassAt(classCursor, attempt);
            boolean canContinue = switch (holderClass) {
                case 0 -> scanOnePlayer(level);
                case 1 -> scanOneDroppedItem(level);
                case 2 -> scanOneBlockInventory(level);
                default -> scanOnePlacedMaterial(level);
            };
            surfaceClassCursors.put(level, surfaceClassAfter(holderClass));
            if (!canContinue) return;
        }
    }

    private boolean scanOnePlayer(ServerLevel level) {
        boolean[] canContinue = { true };
        players(level).visit(1, uuid -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player == null || player.level() != level) {
                playerSlotCursors.remove(uuid);
                return ActiveHolderSet.Decision.REMOVE;
            }
            canContinue[0] = scanOnePlayerStack(level, player);
            return canContinue[0] ? ActiveHolderSet.Decision.KEEP : ActiveHolderSet.Decision.STOP;
        });
        return canContinue[0];
    }

    private boolean scanOnePlayerStack(ServerLevel level, ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();
        int start = Math.floorMod(playerSlotCursors.getOrDefault(player.getUUID(), 0), Math.max(1, size));
        NuclearSimulationService.NuclearEnvironment baseEnvironment = NuclearSimulationService.environment(level, player.blockPosition());
        for (int offset = 0; offset < size; offset++) {
            int slot = (start + offset) % size;
            ItemStack stack = inventory.getItem(slot);
            NuclearSimulationService.NuclearEnvironment environment = inventoryEnvironment(inventory, slot, baseEnvironment);
            if (!NuclearSimulationService.INSTANCE.canProcessStack(stack, environment)) continue;
            playerSlotCursors.put(player.getUUID(), advanceCursor(slot, size, 1));
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) return false;
            return processPlayerStack(level, player, inventory, slot, stack, environment)
                != NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED;
        }
        playerSlotCursors.put(player.getUUID(), (start + 1) % Math.max(1, size));
        return true;
    }

    private boolean scanOneDroppedItem(ServerLevel level) {
        boolean[] canContinue = { true };
        droppedItems(level).visit(1, uuid -> {
            if (!(level.getEntity(uuid) instanceof ItemEntity item) || !item.isAlive() || !isRelevant(item.getItem())) {
                return ActiveHolderSet.Decision.REMOVE;
            }
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) {
                canContinue[0] = false;
                return ActiveHolderSet.Decision.STOP;
            }
            advectInLava(level, item);
            ItemStack stack = item.getItem();
            NuclearSimulationService.ProcessStatus status = NuclearSimulationService.INSTANCE.processStack(
                level, item.blockPosition(), stack, 1.0, null,
                output -> Containers.dropItemStack(level, item.getX(), item.getY(), item.getZ(), output)
            );
            if (stack.isEmpty()) {
                item.discard();
                return ActiveHolderSet.Decision.REMOVE;
            }
            item.setItem(stack);
            if (status == NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED) {
                canContinue[0] = false;
                return ActiveHolderSet.Decision.STOP;
            }
            return ActiveHolderSet.Decision.KEEP;
        });
        return canContinue[0];
    }

    private boolean scanOneBlockInventory(ServerLevel level) {
        boolean[] canContinue = { true };
        blockInventories(level).visit(1, pos -> {
            BlockEntity blockEntity = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
            if (blockEntity == null) {
                blockInventorySlotCursors(level).remove(pos);
                return ActiveHolderSet.Decision.REMOVE;
            }
            Optional<IItemHandler> optional = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
            if (optional.isEmpty() || !hasRelevantStack(optional.get())) {
                blockInventorySlotCursors(level).remove(pos);
                return ActiveHolderSet.Decision.REMOVE;
            }
            if (!scanBlockEntityInventory(level, blockEntity)) {
                canContinue[0] = false;
                return ActiveHolderSet.Decision.STOP;
            }
            return hasRelevantStack(optional.get()) ? ActiveHolderSet.Decision.KEEP : ActiveHolderSet.Decision.REMOVE;
        });
        return canContinue[0];
    }

    private boolean scanOnePlacedMaterial(ServerLevel level) {
        boolean[] canContinue = { true };
        placedMaterials(level).visit(1, pos -> scanPlacedMaterial(level, pos, canContinue));
        return canContinue[0];
    }

    private ActiveHolderSet.Decision scanPlacedMaterial(ServerLevel level, BlockPos pos, boolean[] canContinue) {
        if (!level.isLoaded(pos)) return ActiveHolderSet.Decision.REMOVE;
        PlacedNuclearData data = PlacedNuclearData.get(level);
        PlacedNuclearData.Entry entry = data.get(pos).orElse(null);
        if (entry == null) return ActiveHolderSet.Decision.REMOVE;
        if (!PlacedNuclearResolver.INSTANCE.matches(level.getBlockState(pos), entry)) {
            data.remove(pos);
            return ActiveHolderSet.Decision.REMOVE;
        }
        long elapsedTicks = Math.max(0L, level.getGameTime() - entry.processedGameTime());
        if (elapsedTicks <= 0L) return ActiveHolderSet.Decision.KEEP;
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) {
            canContinue[0] = false;
            return ActiveHolderSet.Decision.STOP;
        }
        LoadedExposureClock.Window exposure = entry.exposureWindow(elapsedTicks);
        NuclearSimulationService.StateProcessResult result = NuclearSimulationService.INSTANCE.processPlacedState(
            level, pos, entry.state(), elapsedTicks / 20.0,
            NuclearSimulationService.environment(level, pos),
            NuclearSimulationService.heatStorage(level.getBlockEntity(pos)),
            net.minecraft.util.RandomSource.create(LoadedExposureClock.deterministicSeed(exposure, "placed-state"))
        );
        if (result.budgetExhausted()) {
            canContinue[0] = false;
            return ActiveHolderSet.Decision.STOP;
        }
        data.put(pos, entry.advance(result.state(), elapsedTicks, level.getGameTime()));
        return ActiveHolderSet.Decision.KEEP;
    }

    /** Deterministic live-server proof seam; production scheduling still uses bounded round-robin visits. */
    public void scanPlacedNow(ServerLevel level, BlockPos pos) {
        placedMaterials(level).add(pos.immutable());
        boolean[] canContinue = { true };
        ActiveHolderSet.Decision decision = scanPlacedMaterial(level, pos, canContinue);
        if (decision == ActiveHolderSet.Decision.REMOVE) placedMaterials(level).remove(pos);
    }

    /** Deterministic live-server proof seam using the production per-holder slot cursor and budgets. */
    public boolean scanBlockInventoryNow(ServerLevel level, BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.getLevel() != level) return false;
        blockInventories(level).add(blockEntity.getBlockPos().immutable());
        return scanBlockEntityInventory(level, blockEntity);
    }

    private static void advectInLava(ServerLevel level, ItemEntity item) {
        BlockPos pos = item.blockPosition();
        var fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.LAVA)) return;
        item.setDeltaMovement(LavaAdvectionMath.advect(item.getDeltaMovement(), fluid.getFlow(level, pos)));
        item.hurtMarked = true;
    }

    private boolean scanBlockEntityInventory(ServerLevel level, BlockEntity blockEntity) {
        Optional<IItemHandler> optional = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (optional.isEmpty() || !(optional.get() instanceof IItemHandlerModifiable handler)) return true;
        if (!hasRelevantStack(handler)) return true;
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) return false;
        int size = handler.getSlots();
        if (size <= 0) return true;
        BlockPos holderPos = blockEntity.getBlockPos().immutable();
        Map<BlockPos, Integer> cursors = blockInventorySlotCursors(level);
        int start = Math.floorMod(cursors.getOrDefault(holderPos, 0), size);
        NuclearSimulationService.NuclearEnvironment baseEnvironment = NuclearSimulationService.environment(level, blockEntity.getBlockPos());
        for (int offset = 0; offset < size; offset++) {
            int slot = slotAt(start, size, offset);
            ItemStack current = handler.getStackInSlot(slot);
            NuclearSimulationService.NuclearEnvironment environment = inventoryEnvironment(handler, slot, baseEnvironment);
            if (!NuclearSimulationService.INSTANCE.canProcessStack(current, environment)) continue;
            cursors.put(holderPos, advanceCursor(slot, size, 1));
            ItemStack working = current.copy();
            NuclearSimulationService.ProcessStatus status = processHandlerStack(level, blockEntity, handler, slot, working, environment);
            boolean exposureAdvanced = !ItemStack.matches(current, working);
            if (status == NuclearSimulationService.ProcessStatus.MUTATED || exposureAdvanced) {
                handler.setStackInSlot(slot, working);
                blockEntity.setChanged();
            }
            if (status == NuclearSimulationService.ProcessStatus.MUTATED) {
                return true;
            }
            if (status == NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED) return false;
        }
        cursors.put(holderPos, advanceCursor(start, size, 1));
        return true;
    }

    private static boolean hasRelevantStack(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (NuclearSimulationService.INSTANCE.isNuclearRelevant(stack) || NuclearSimulationService.INSTANCE.hasCaptureProduct(stack)) return true;
        }
        return false;
    }

    private static boolean isRelevant(ItemStack stack) {
        return NuclearSimulationService.INSTANCE.isNuclearRelevant(stack)
            || NuclearSimulationService.INSTANCE.hasCaptureProduct(stack);
    }

    private ActiveHolderSet<UUID> droppedItems(ServerLevel level) {
        return activeDroppedItems.computeIfAbsent(level, ignored -> new ActiveHolderSet<>());
    }

    private ActiveHolderSet<UUID> players(ServerLevel level) {
        return activePlayers.computeIfAbsent(level, ignored -> new ActiveHolderSet<>());
    }

    private ActiveHolderSet<BlockPos> blockInventories(ServerLevel level) {
        return activeBlockInventories.computeIfAbsent(level, ignored -> new ActiveHolderSet<>());
    }

    private Map<BlockPos, Integer> blockInventorySlotCursors(ServerLevel level) {
        return blockInventorySlotCursors.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
    }

    private ActiveHolderSet<BlockPos> placedMaterials(ServerLevel level) {
        return activePlacedMaterials.computeIfAbsent(level, ignored -> new ActiveHolderSet<>());
    }

    private NuclearSimulationService.ProcessStatus processPlayerStack(ServerLevel level, ServerPlayer player, Inventory inventory, int slot, ItemStack stack, NuclearSimulationService.NuclearEnvironment environment) {
        NuclearSimulationService.ProcessStatus status = NuclearSimulationService.INSTANCE.processStack(
            level, player.blockPosition(), stack, PLAYER_PERIOD_TICKS / 20.0, environment, null,
            event -> event.type() != NuclearSimulationService.NuclearEventType.CAPTURE
                || canPlaceAdjacent(inventory, slot, outputStack(event)),
            (type, output) -> placePlayerOutput(player, inventory, slot, type, output)
        );
        inventory.setItem(slot, stack);
        inventory.setChanged();
        return status;
    }

    private NuclearSimulationService.ProcessStatus processHandlerStack(ServerLevel level, BlockEntity blockEntity, IItemHandlerModifiable handler, int slot, ItemStack working, NuclearSimulationService.NuclearEnvironment environment) {
        return NuclearSimulationService.INSTANCE.processStack(
            level, blockEntity.getBlockPos(), working, 1.0, environment, NuclearSimulationService.heatSink(blockEntity),
            event -> event.type() != NuclearSimulationService.NuclearEventType.CAPTURE
                || canPlaceAdjacent(handler, slot, outputStack(event)),
            (type, output) -> placeHandlerOutput(level, blockEntity.getBlockPos(), handler, slot, type, output)
        );
    }

    private static NuclearSimulationService.NuclearEnvironment inventoryEnvironment(Inventory inventory, int slot, NuclearSimulationService.NuclearEnvironment baseEnvironment) {
        double externalFlux = adjacentFlux(baseEnvironment, index -> inventory.getItem(index), inventory.getContainerSize(), slot);
        return new NuclearSimulationService.NuclearEnvironment(
            baseEnvironment.moderation(), baseEnvironment.absorption(), externalFlux, baseEnvironment.contactFraction()
        );
    }

    private static NuclearSimulationService.NuclearEnvironment inventoryEnvironment(IItemHandler handler, int slot, NuclearSimulationService.NuclearEnvironment baseEnvironment) {
        double externalFlux = adjacentFlux(baseEnvironment, handler::getStackInSlot, handler.getSlots(), slot);
        return new NuclearSimulationService.NuclearEnvironment(
            baseEnvironment.moderation(), baseEnvironment.absorption(), externalFlux, baseEnvironment.contactFraction()
        );
    }

    private static double adjacentFlux(NuclearSimulationService.NuclearEnvironment baseEnvironment, java.util.function.IntFunction<ItemStack> stackGetter, int size, int slot) {
        double flux = 0.0;
        for (int candidate : adjacentSlots(slot, size)) {
            flux += NuclearSimulationService.INSTANCE.intrinsicFlux(stackGetter.apply(candidate), baseEnvironment);
        }
        return flux;
    }

    private static List<Integer> adjacentSlots(int slot, int size) {
        List<Integer> slots = new ArrayList<>(2);
        if (slot > 0) slots.add(slot - 1);
        if (slot + 1 < size) slots.add(slot + 1);
        return slots;
    }

    private static boolean canPlaceAdjacent(Inventory inventory, int slot, ItemStack output) {
        for (int candidate : adjacentSlots(slot, inventory.getContainerSize())) {
            if (canInsertIntoStack(inventory.getItem(candidate), output)) return true;
        }
        return false;
    }

    private static boolean canPlaceAdjacent(IItemHandler handler, int slot, ItemStack output) {
        for (int candidate : adjacentSlots(slot, handler.getSlots())) {
            ItemStack remaining = handler.insertItem(candidate, output.copy(), true);
            if (remaining.isEmpty()) return true;
        }
        return false;
    }

    private static void placePlayerOutput(ServerPlayer player, Inventory inventory, int sourceSlot, NuclearSimulationService.NuclearEventType type, ItemStack output) {
        if (output.isEmpty()) return;
        boolean inserted = switch (type) {
            case CAPTURE -> insertAdjacent(inventory, sourceSlot, output);
            case FISSION -> insertRandom(inventory, output, player.getRandom());
            case DECAY -> inventory.add(output);
        };
        if (!inserted && !output.isEmpty()) player.drop(output, false);
    }

    private static void placeHandlerOutput(ServerLevel level, BlockPos pos, IItemHandlerModifiable handler, int sourceSlot, NuclearSimulationService.NuclearEventType type, ItemStack output) {
        if (output.isEmpty()) return;
        boolean inserted = switch (type) {
            case CAPTURE -> insertAdjacent(handler, sourceSlot, output);
            case FISSION -> insertRandom(handler, output, level.getRandom());
            case DECAY -> insert(handler, output);
        };
        if (!inserted && !output.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, output);
        }
    }

    private static boolean insertAdjacent(Inventory inventory, int sourceSlot, ItemStack output) {
        for (int candidate : adjacentSlots(sourceSlot, inventory.getContainerSize())) {
            if (insertIntoInventorySlot(inventory, candidate, output)) return true;
        }
        return false;
    }

    private static boolean insertAdjacent(IItemHandler handler, int sourceSlot, ItemStack output) {
        for (int candidate : adjacentSlots(sourceSlot, handler.getSlots())) {
            ItemStack remaining = handler.insertItem(candidate, output, false);
            if (remaining.isEmpty()) return true;
            output.setCount(remaining.getCount());
        }
        return false;
    }

    private static boolean insertRandom(Inventory inventory, ItemStack output, net.minecraft.util.RandomSource random) {
        List<Integer> slots = shuffledSlots(inventory.getContainerSize(), random);
        for (int slot : slots) {
            if (insertIntoInventorySlot(inventory, slot, output)) return true;
        }
        return false;
    }

    private static boolean insertRandom(IItemHandler handler, ItemStack output, net.minecraft.util.RandomSource random) {
        List<Integer> slots = shuffledSlots(handler.getSlots(), random);
        for (int slot : slots) {
            ItemStack remaining = handler.insertItem(slot, output, false);
            if (remaining.isEmpty()) return true;
            output.setCount(remaining.getCount());
        }
        return false;
    }

    private static List<Integer> shuffledSlots(int size, net.minecraft.util.RandomSource random) {
        List<Integer> slots = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) slots.add(slot);
        Collections.shuffle(slots, new java.util.Random(random.nextLong()));
        return slots;
    }

    private static boolean insert(IItemHandlerModifiable handler, ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        stack.setCount(remaining.getCount());
        return remaining.isEmpty();
    }

    private static boolean insertIntoInventorySlot(Inventory inventory, int slot, ItemStack output) {
        ItemStack target = inventory.getItem(slot);
        if (!canInsertIntoStack(target, output)) return false;
        if (target.isEmpty()) {
            inventory.setItem(slot, output.copy());
            output.setCount(0);
            return true;
        }
        int space = Math.min(target.getMaxStackSize(), inventory.getMaxStackSize()) - target.getCount();
        int moved = Math.min(space, output.getCount());
        if (moved <= 0) return false;
        target.grow(moved);
        output.shrink(moved);
        return output.isEmpty();
    }

    private static boolean canInsertIntoStack(ItemStack target, ItemStack output) {
        if (target.isEmpty()) return true;
        return ItemStack.isSameItemSameTags(target, output)
            && target.getCount() < Math.min(target.getMaxStackSize(), output.getMaxStackSize());
    }

    private static ItemStack outputStack(NuclearSimulationService.NuclearStackEvent event) {
        return new ItemStack(event.outputItem(), event.outputCount());
    }

}
