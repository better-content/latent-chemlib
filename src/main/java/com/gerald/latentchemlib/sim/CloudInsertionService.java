package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The only supported boundary for introducing matter into the ambient Latent simulation.
 *
 * <p>The service deliberately does not own a queue or a second work budget. Once inserted,
 * matter is governed by the ordinary chemical-cloud ticker and {@link SimulationScheduler}.
 */
public final class CloudInsertionService {
    public static final CloudInsertionService INSTANCE = new CloudInsertionService();
    public static final double MASS_PER_ADPOTHER_UNIT = 16.0;

    private CloudInsertionService() {}

    public InsertionResult insert(ServerLevel level, BlockPos origin, ChemicalState state) {
        if (state.mass() <= 0.0) return InsertionResult.rejected(state);
        List<BlockPos> candidates = candidateOffsets(state.chemicalId()).stream()
            .map(origin::offset)
            .map(BlockPos::immutable)
            .filter(level::isInWorldBounds)
            .filter(level::isLoaded)
            .toList();

        for (BlockPos pos : candidates) {
            if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity existing) {
                existing.seed(state);
                return InsertionResult.accepted(state, pos);
            }
        }
        for (BlockPos pos : candidates) {
            if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity) continue;
            if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) continue;
            if (!level.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3)) continue;
            if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud) {
                cloud.seed(state);
                return InsertionResult.accepted(state, pos);
            }
        }
        return InsertionResult.rejected(state);
    }

    static List<BlockPos> candidateOffsets(String chemicalId) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    offsets.add(new BlockPos(x, y, z));
                }
            }
        }
        int chemicalHash = chemicalId.hashCode();
        offsets.sort(
            Comparator.comparingInt((BlockPos pos) -> pos.distManhattan(BlockPos.ZERO))
                .thenComparingInt(pos -> Integer.rotateLeft(pos.hashCode() ^ chemicalHash, 13))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
        );
        return List.copyOf(offsets);
    }

    public int insertAdpotherUnits(ServerLevel level, BlockPos origin, ChemicalState perUnitState, int units) {
        int boundedUnits = Math.max(0, units);
        if (boundedUnits == 0) return 0;
        ChemicalState payload = scale(perUnitState, boundedUnits);
        return insert(level, origin, payload).acceptedMass() > 0.0 ? boundedUnits : 0;
    }

    static ChemicalState scale(ChemicalState perUnitState, int units) {
        int boundedUnits = Math.max(0, units);
        return perUnitState.withMass(perUnitState.mass() * boundedUnits);
    }

    public record InsertionResult(double acceptedMass, double rejectedMass, BlockPos target) {
        static InsertionResult accepted(ChemicalState state, BlockPos target) {
            return new InsertionResult(state.mass(), 0.0, target.immutable());
        }

        static InsertionResult rejected(ChemicalState state) {
            return new InsertionResult(0.0, Math.max(0.0, state.mass()), null);
        }

        public boolean acceptedAll() {
            return acceptedMass > 0.0 && rejectedMass == 0.0;
        }
    }
}
