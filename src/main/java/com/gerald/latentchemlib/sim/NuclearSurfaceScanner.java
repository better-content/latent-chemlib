package com.gerald.latentchemlib.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class NuclearSurfaceScanner {
    public static final NuclearSurfaceScanner INSTANCE = new NuclearSurfaceScanner();
    private static final int PLAYER_PERIOD_TICKS = 40;
    private static final int BLOCK_CHUNK_RADIUS = 2;
    private static final double DROPPED_ITEM_RADIUS = 96.0;

    private final Map<ResourceKey<Level>, Integer> blockChunkCursor = new HashMap<>();
    private final Map<ResourceKey<Level>, Integer> blockEntityCursor = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || event.player.tickCount % PLAYER_PERIOD_TICKS != 0) return;
        if (!(event.player instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        scanPlayerInventory(level, player);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || event.level.getGameTime() % 20L != 0L) return;
        if (!(event.level instanceof ServerLevel level)) return;
        scanDroppedItems(level);
        scanBlockInventories(level);
    }

    static int advanceCursor(int cursor, int size, int advanced) {
        if (size <= 0) return 0;
        int next = cursor + Math.max(0, advanced);
        return next % size;
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

    private void scanDroppedItems(ServerLevel level) {
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            AABB box = player.getBoundingBox().inflate(DROPPED_ITEM_RADIUS);
            List<ItemEntity> items = level.getEntities(EntityType.ITEM, box, item -> item.isAlive() && seen.add(item.getUUID()));
            for (ItemEntity item : items) {
                ItemStack stack = item.getItem();
                if (!NuclearSimulationService.INSTANCE.isNuclearRelevant(stack)) continue;
                if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) return;
                NuclearSimulationService.ProcessStatus status = NuclearSimulationService.INSTANCE.processStack(
                    level,
                    item.blockPosition(),
                    stack,
                    1.0,
                    null,
                    output -> Containers.dropItemStack(level, item.getX(), item.getY(), item.getZ(), output)
                );
                if (stack.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(stack);
                }
                if (status == NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED) return;
            }
        }
    }

    private void scanBlockInventories(ServerLevel level) {
        List<ChunkPos> chunks = candidateChunks(level);
        if (chunks.isEmpty()) return;
        ResourceKey<Level> dimension = level.dimension();
        int cursor = Math.floorMod(blockChunkCursor.getOrDefault(dimension, 0), chunks.size());
        int checkedChunks = 0;
        while (checkedChunks < chunks.size()) {
            ChunkPos chunkPos = chunks.get(cursor);
            if (!scanChunkBlockInventories(level, dimension, chunkPos)) {
                blockChunkCursor.put(dimension, cursor);
                return;
            }
            checkedChunks++;
            cursor = advanceCursor(cursor, chunks.size(), 1);
            blockEntityCursor.put(dimension, 0);
        }
        blockChunkCursor.put(dimension, cursor);
    }

    private boolean scanChunkBlockInventories(ServerLevel level, ResourceKey<Level> dimension, ChunkPos chunkPos) {
        ChunkAccess access = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
        if (!(access instanceof LevelChunk chunk)) return true;
        List<BlockEntity> blockEntities = new ArrayList<>(chunk.getBlockEntities().values());
        if (blockEntities.isEmpty()) return true;
        int cursor = Math.floorMod(blockEntityCursor.getOrDefault(dimension, 0), blockEntities.size());
        int checked = 0;
        while (checked < blockEntities.size()) {
            BlockEntity blockEntity = blockEntities.get(cursor);
            if (!scanBlockEntityInventory(level, blockEntity)) {
                blockEntityCursor.put(dimension, cursor);
                return false;
            }
            checked++;
            cursor = advanceCursor(cursor, blockEntities.size(), 1);
        }
        blockEntityCursor.put(dimension, 0);
        return true;
    }

    private boolean scanBlockEntityInventory(ServerLevel level, BlockEntity blockEntity) {
        Optional<IItemHandler> optional = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (optional.isEmpty() || !(optional.get() instanceof IItemHandlerModifiable handler)) return true;
        if (!hasRelevantStack(handler)) return true;
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_SURFACE_SCANS, 1)) return false;
        NuclearSimulationService.NuclearEnvironment baseEnvironment = NuclearSimulationService.environment(level, blockEntity.getBlockPos());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack current = handler.getStackInSlot(slot);
            NuclearSimulationService.NuclearEnvironment environment = inventoryEnvironment(handler, slot, baseEnvironment);
            if (!NuclearSimulationService.INSTANCE.canProcessStack(current, environment)) continue;
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
        return true;
    }

    private static boolean hasRelevantStack(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (NuclearSimulationService.INSTANCE.isNuclearRelevant(stack) || NuclearSimulationService.INSTANCE.hasCaptureProduct(stack)) return true;
        }
        return false;
    }

    private NuclearSimulationService.ProcessStatus processPlayerStack(ServerLevel level, ServerPlayer player, Inventory inventory, int slot, ItemStack stack, NuclearSimulationService.NuclearEnvironment environment) {
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STACK_EVALUATIONS, 1)) {
            return NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED;
        }
        Optional<NuclearSimulationService.NuclearStackEvent> event = NuclearSimulationService.INSTANCE.evaluateStack(
            stack,
            PLAYER_PERIOD_TICKS / 20.0,
            environment,
            level.getRandom()
        );
        if (event.isEmpty()) {
            inventory.setItem(slot, stack);
            inventory.setChanged();
            return NuclearSimulationService.ProcessStatus.UNCHANGED;
        }
        NuclearSimulationService.NuclearStackEvent nuclearEvent = event.get();
        if (nuclearEvent.type() == NuclearSimulationService.NuclearEventType.CAPTURE && !canPlaceAdjacent(inventory, slot, outputStack(nuclearEvent))) {
            return NuclearSimulationService.ProcessStatus.UNCHANGED;
        }
        NuclearSimulationService.ProcessStatus status = NuclearSimulationService.INSTANCE.applyStackEvent(
            level,
            player.blockPosition(),
            stack,
            nuclearEvent,
            null,
            output -> placePlayerOutput(player, inventory, slot, nuclearEvent.type(), output)
        );
        inventory.setItem(slot, stack);
        inventory.setChanged();
        return status;
    }

    private NuclearSimulationService.ProcessStatus processHandlerStack(ServerLevel level, BlockEntity blockEntity, IItemHandlerModifiable handler, int slot, ItemStack working, NuclearSimulationService.NuclearEnvironment environment) {
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STACK_EVALUATIONS, 1)) {
            return NuclearSimulationService.ProcessStatus.BUDGET_EXHAUSTED;
        }
        Optional<NuclearSimulationService.NuclearStackEvent> event = NuclearSimulationService.INSTANCE.evaluateStack(
            working,
            1.0,
            environment,
            level.getRandom()
        );
        if (event.isEmpty()) return NuclearSimulationService.ProcessStatus.UNCHANGED;
        NuclearSimulationService.NuclearStackEvent nuclearEvent = event.get();
        if (nuclearEvent.type() == NuclearSimulationService.NuclearEventType.CAPTURE && !canPlaceAdjacent(handler, slot, outputStack(nuclearEvent))) {
            return NuclearSimulationService.ProcessStatus.UNCHANGED;
        }
        return NuclearSimulationService.INSTANCE.applyStackEvent(
            level,
            blockEntity.getBlockPos(),
            working,
            nuclearEvent,
            NuclearSimulationService.heatSink(blockEntity),
            output -> placeHandlerOutput(level, blockEntity.getBlockPos(), handler, slot, nuclearEvent.type(), output)
        );
    }

    private static NuclearSimulationService.NuclearEnvironment inventoryEnvironment(Inventory inventory, int slot, NuclearSimulationService.NuclearEnvironment baseEnvironment) {
        double externalFlux = adjacentFlux(baseEnvironment, index -> inventory.getItem(index), inventory.getContainerSize(), slot);
        return new NuclearSimulationService.NuclearEnvironment(baseEnvironment.moderation(), baseEnvironment.absorption(), externalFlux);
    }

    private static NuclearSimulationService.NuclearEnvironment inventoryEnvironment(IItemHandler handler, int slot, NuclearSimulationService.NuclearEnvironment baseEnvironment) {
        double externalFlux = adjacentFlux(baseEnvironment, handler::getStackInSlot, handler.getSlots(), slot);
        return new NuclearSimulationService.NuclearEnvironment(baseEnvironment.moderation(), baseEnvironment.absorption(), externalFlux);
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

    private static List<ChunkPos> candidateChunks(ServerLevel level) {
        Set<Long> seen = new HashSet<>();
        List<ChunkPos> chunks = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dz = -BLOCK_CHUNK_RADIUS; dz <= BLOCK_CHUNK_RADIUS; dz++) {
                for (int dx = -BLOCK_CHUNK_RADIUS; dx <= BLOCK_CHUNK_RADIUS; dx++) {
                    ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                    if (seen.add(chunk.toLong())) chunks.add(chunk);
                }
            }
        }
        return chunks;
    }
}
