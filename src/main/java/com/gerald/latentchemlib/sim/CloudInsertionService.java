package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
        for (int radius = 0; radius <= 2; radius++) {
            for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius)
            )) {
                BlockPos pos = candidate.immutable();
                if (!level.isInWorldBounds(pos)) continue;
                if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity existing) {
                    ChemicalState current = existing.chemicalState();
                    if (current.mass() > 0.0 && !current.chemicalId().equals(state.chemicalId())) continue;
                    existing.seed(state);
                    return InsertionResult.accepted(state);
                }
                if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) continue;
                if (!level.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3)) continue;
                if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud) {
                    cloud.seed(state);
                    return InsertionResult.accepted(state);
                }
            }
        }
        return InsertionResult.rejected(state);
    }

    public int insertAdpotherUnits(ServerLevel level, BlockPos origin, ChemicalState perUnitState, int units) {
        int boundedUnits = Math.max(0, units);
        if (boundedUnits == 0) return 0;
        ChemicalState payload = scale(perUnitState, boundedUnits);
        return insert(level, origin, payload).acceptedMass() > 0.0 ? boundedUnits : 0;
    }

    static ChemicalState scale(ChemicalState perUnitState, int units) {
        int boundedUnits = Math.max(0, units);
        return new ChemicalState(
            perUnitState.chemicalId(),
            perUnitState.mass() * boundedUnits,
            perUnitState.density() * boundedUnits,
            perUnitState.temperature(),
            perUnitState.charge(),
            perUnitState.energy() * boundedUnits
        );
    }

    public record InsertionResult(double acceptedMass, double rejectedMass) {
        static InsertionResult accepted(ChemicalState state) {
            return new InsertionResult(state.mass(), 0.0);
        }

        static InsertionResult rejected(ChemicalState state) {
            return new InsertionResult(0.0, Math.max(0.0, state.mass()));
        }

        public boolean acceptedAll() {
            return acceptedMass > 0.0 && rejectedMass == 0.0;
        }
    }
}
