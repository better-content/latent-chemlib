package com.bettercontent.latentchemlib.integration.adpother;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import com.endertech.minecraft.mods.adpother.pollution.GasExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class GasFireballEffects {
    private static final TagKey<net.minecraft.world.level.block.Block> FRAGILE_BLOCKS = BlockTags.create(
        new ResourceLocation(LatentChemlibMod.MOD_ID, "gas_fireball_fragile")
    );
    private static final int MAX_PARTICLE_ANCHORS = 256;
    private static final int MAX_FRAGILE_CHANGES = 512;
    private static final int MAX_FIRE_PLACEMENTS = 256;

    private GasFireballEffects() {}

    static void apply(ServerLevel level, List<BlockPos> cloudPositions, GasFireballSourceEntity source) {
        if (cloudPositions.isEmpty()) return;
        List<BlockPos> anchors = distributedOrder(cloudPositions);
        sendParticles(level, anchors);
        heatEntities(level, cloudPositions);

        Set<BlockPos> affected = new LinkedHashSet<>();
        for (BlockPos cloudPos : cloudPositions) {
            affected.add(cloudPos);
            for (Direction direction : Direction.values()) affected.add(cloudPos.relative(direction));
        }
        List<BlockPos> orderedAffected = distributedOrder(affected);
        breakFragileBlocks(level, orderedAffected, source);
        placeFire(level, orderedAffected);
    }

    private static void sendParticles(ServerLevel level, List<BlockPos> anchors) {
        int count = Math.min(MAX_PARTICLE_ANCHORS, anchors.size());
        for (int i = 0; i < count; i++) {
            BlockPos pos = anchors.get(i);
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 12, 1.35, 1.35, 1.35, 0.04);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 4, 1.1, 1.1, 1.1, 0.025);
            if (i % 8 == 0) level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void heatEntities(ServerLevel level, List<BlockPos> cloudPositions) {
        AABB bounds = boundsOf(cloudPositions).inflate(2.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            AABB heatBox = entity.getBoundingBox().inflate(2.0);
            boolean reached = cloudPositions.stream().anyMatch(pos -> heatBox.intersects(new AABB(pos)));
            if (!reached) continue;
            entity.hurt(level.damageSources().inFire(), 4.0f);
            entity.setSecondsOnFire(8);
        }
    }

    private static void breakFragileBlocks(ServerLevel level, List<BlockPos> positions, GasFireballSourceEntity source) {
        int changed = 0;
        for (BlockPos pos : positions) {
            if (changed >= MAX_FRAGILE_CHANGES) return;
            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!state.is(FRAGILE_BLOCKS) || blockEntity != null) continue;
            if (level.destroyBlock(pos, false, source, 512)) changed++;
        }
    }

    private static void placeFire(ServerLevel level, List<BlockPos> positions) {
        if (!GasExplosion.setOnFire.get()) return;
        int placed = 0;
        for (BlockPos pos : positions) {
            if (placed >= MAX_FIRE_PLACEMENTS) return;
            if (Math.floorMod(Long.hashCode(pos.asLong()), 3) != 0 || !level.getBlockState(pos).isAir()) continue;
            BlockState fire = BaseFireBlock.getState(level, pos);
            if (fire.canSurvive(level, pos) && level.setBlock(pos, fire, 3)) placed++;
        }
    }

    private static AABB boundsOf(List<BlockPos> positions) {
        BlockPos first = positions.get(0);
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    private static List<BlockPos> distributedOrder(Iterable<BlockPos> positions) {
        List<BlockPos> ordered = new ArrayList<>();
        positions.forEach(pos -> ordered.add(pos.immutable()));
        ordered.sort(Comparator
            .comparingInt((BlockPos pos) -> Integer.rotateLeft(Long.hashCode(pos.asLong()), 13))
            .thenComparingLong(BlockPos::asLong));
        return ordered;
    }
}
