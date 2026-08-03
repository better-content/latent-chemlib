package com.gerald.latentchemlib.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded thermal failure: at most one immediately surrounding solid is melted per event. */
public final class ThermalMelting {
    private ThermalMelting() {}

    public static MeltResult meltNext(ServerLevel level, BlockPos origin, int cursor) {
        Direction[] directions = Direction.values();
        for (int offset = 0; offset < directions.length; offset++) {
            int index = Math.floorMod(cursor + offset, directions.length);
            BlockPos target = origin.relative(directions[index]);
            BlockState state = level.getBlockState(target);
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (blockEntity != null || state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.getDestroySpeed(level, target) < 0.0f || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) continue;
            level.setBlock(target, Blocks.LAVA.defaultBlockState(), 3);
            return new MeltResult(true, (index + 1) % directions.length, target);
        }
        return new MeltResult(false, Math.floorMod(cursor + 1, directions.length), origin);
    }

    public record MeltResult(boolean melted, int nextCursor, BlockPos position) {}
}
