package com.bettercontent.latentchemlib.integration.adpother;

import com.endertech.minecraft.mods.adpother.blocks.AbstractGas;
import com.endertech.minecraft.mods.adpother.pollution.WorldData;
import com.bettercontent.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.bettercontent.latentchemlib.sim.CloudInsertionService;
import com.bettercontent.latentchemlib.sim.GasHazardMath;
import com.bettercontent.latentchemlib.sim.SimulationBudget;
import com.bettercontent.latentchemlib.sim.SimulationScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class LatentGasHazardService {
    public static final LatentGasHazardService INSTANCE = new LatentGasHazardService();

    private LatentGasHazardService() {}

    public Detection detectAround(ServerLevel level, BlockPos center, int radius) {
        double massInsideRadius = 0.0;
        boolean explosionRisk = false;
        Set<BlockPos> assessed = new HashSet<>();
        int chunkRadius = Math.max(1, (radius + 15) / 16);
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        double radiusSquared = (double) radius * radius;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ChemicalCloudBlockEntity cloud)
                        || blockEntity.getBlockPos().distSqr(center) > radiusSquared) continue;
                    massInsideRadius += cloud.chemicalState().mass();
                    BlockPos pos = blockEntity.getBlockPos();
                    if (assessed.contains(pos) || flammableGas(cloud).isEmpty()) continue;
                    Component component = assess(level, pos);
                    assessed.addAll(component.positions());
                    explosionRisk |= component.complete() && component.explosiveFraction() >= 1.0;
                }
            }
        }
        return new Detection(explosionRisk, GasHazardMath.wholeUnits(massInsideRadius));
    }

    public boolean tryIgnite(ServerLevel level, BlockPos seed) {
        if (!(level.getBlockEntity(seed) instanceof ChemicalCloudBlockEntity cloud)
            || flammableGas(cloud).isEmpty()
            || !hasIgnitionSource(level, seed)) return false;
        return explodeIfReady(level, assess(level, seed));
    }

    public boolean tryIgniteAtAny(ServerLevel level, Iterable<BlockPos> seeds) {
        for (BlockPos seed : seeds) {
            if (!(level.getBlockEntity(seed) instanceof ChemicalCloudBlockEntity cloud)
                || flammableGas(cloud).isEmpty()) continue;
            if (explodeIfReady(level, assess(level, seed))) return true;
        }
        return false;
    }

    Component assess(ServerLevel level, BlockPos seed) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> positions = new ArrayList<>();
        List<GasHazardMath.Contribution> contributions = new ArrayList<>();
        double totalUnits = 0.0;
        double weightedX = 0.0;
        double weightedY = 0.0;
        double weightedZ = 0.0;
        boolean complete = true;
        open.add(seed.immutable());

        while (!open.isEmpty()) {
            BlockPos pos = open.removeFirst();
            if (!visited.add(pos)) continue;
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NEIGHBOR_OPS, 1)) {
                complete = false;
                break;
            }
            if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) continue;
            Optional<AbstractGas> gas = flammableGas(cloud);
            if (gas.isEmpty()) continue;
            double units = Math.max(
                0.0,
                cloud.chemicalState().mass() / CloudInsertionService.MASS_PER_ADPOTHER_UNIT
            );
            if (units <= 0.0) continue;
            positions.add(pos.immutable());
            contributions.add(new GasHazardMath.Contribution(units, gas.get().getLowerExplosiveLimit()));
            totalUnits += units;
            weightedX += (pos.getX() + 0.5) * units;
            weightedY += (pos.getY() + 0.5) * units;
            weightedZ += (pos.getZ() + 0.5) * units;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!visited.contains(neighbor) && level.isLoaded(neighbor)) open.addLast(neighbor.immutable());
            }
        }

        Vec3 center = totalUnits <= 0.0
            ? Vec3.atCenterOf(seed)
            : new Vec3(weightedX / totalUnits, weightedY / totalUnits, weightedZ / totalUnits);
        return new Component(
            List.copyOf(positions),
            totalUnits,
            GasHazardMath.explosiveFraction(contributions),
            center,
            complete
        );
    }

    private boolean explodeIfReady(ServerLevel level, Component component) {
        if (!component.complete() || component.explosiveFraction() < 1.0 || component.positions().isEmpty()) {
            return false;
        }
        component.positions().forEach(pos -> {
            if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity) level.removeBlock(pos, false);
        });
        Vec3 center = component.center();
        level.explode(
            null,
            center.x,
            center.y,
            center.z,
            GasHazardMath.blastPower(component.totalUnits()),
            Level.ExplosionInteraction.BLOCK
        );
        return true;
    }

    private boolean hasIgnitionSource(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (WorldData.isIgnitionSource(level, pos.relative(direction))) return true;
        }
        return false;
    }

    private Optional<AbstractGas> flammableGas(ChemicalCloudBlockEntity cloud) {
        return AdpotherCloudView.INSTANCE.selectorFor(cloud.chemicalState())
            .filter(AbstractGas.class::isInstance)
            .map(AbstractGas.class::cast)
            .filter(gas -> gas.getLowerExplosiveLimit() > 0);
    }

    public record Detection(boolean explosionRisk, int gasBlocks) {}

    record Component(
        List<BlockPos> positions,
        double totalUnits,
        double explosiveFraction,
        Vec3 center,
        boolean complete
    ) {}
}
