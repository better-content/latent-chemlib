package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.bettercontent.latentchemlib.data.LatentDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Detects a bounded local collision cell between two geometrically opposing gas streams. */
public final class EmergentFusionService {
    public static final EmergentFusionService INSTANCE = new EmergentFusionService();
    private EmergentFusionService() {}

    public boolean tryFuseAt(ServerLevel level, BlockPos sourcePos, ChemicalCloudBlockEntity source) {
        var profile = LatentDataManager.INSTANCE.nuclearPhenomenaProfile();
        // Unrelated clouds pay no neighbor-search cost.
        if (!NuclearPhenomenaMath.isFusionStreamCandidate(source.chemicalState(), profile)) return false;
        for (Direction direction : Direction.values()) {
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NEIGHBOR_OPS, 2)) return false;
            BlockPos collisionPos = sourcePos.relative(direction);
            ChemicalCloudBlockEntity collision = cloud(level, collisionPos);
            ChemicalCloudBlockEntity opposite = cloud(level, sourcePos.relative(direction, 2));
            if (attempt(level, sourcePos, collisionPos, source, collision, opposite)) return true;
        }
        return false;
    }

    private boolean attempt(
        ServerLevel level,
        BlockPos sourcePos,
        BlockPos collisionPos,
        ChemicalCloudBlockEntity source,
        ChemicalCloudBlockEntity collision,
        ChemicalCloudBlockEntity opposite
    ) {
        if (collision == null || opposite == null || source == opposite) return false;
        var profile = LatentDataManager.INSTANCE.nuclearPhenomenaProfile();
        var planned = NuclearPhenomenaMath.fusion(
            source.chemicalState(), opposite.chemicalState(),
            LatentDataManager.INSTANCE.traits(source.chemicalState().chemicalId()), true, profile
        );
        if (planned.isEmpty()) return false;
        // Both sources defer diffusion, but only one deterministic endpoint owns
        // the mutation. This makes one geometric collision exactly one event.
        if (!isAuthority(sourcePos, opposite.getBlockPos())) return true;
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_STATE_EVALUATIONS, 1)
            || !SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_MUTATIONS, 1)) {
            return true;
        }

        // Qualification is based on each full stream's bulk state. Splitting a
        // reacting batch scales its intensive density, so re-qualifying that
        // extracted portion would incorrectly reject an already valid collision.
        source.extractMass(profile.fusionBatchMassPerStream());
        opposite.extractMass(profile.fusionBatchMassPerStream());
        collision.seed(planned.get().product());
        NuclearSimulationService.INSTANCE.emitAmbientHeat(level, collisionPos, planned.get().heatEmission());
        if (SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_RADIATION_EMISSIONS, 1)) {
            LatentRadiationService.emit(level, collisionPos, profile.fusionRadiationLevel());
        }
        if (planned.get().heatEmission() >= profile.surroundingMeltHeatThreshold()
            && SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NUCLEAR_MUTATIONS, 1)) {
            int cursor = Math.floorMod((int) (level.getGameTime() ^ collisionPos.asLong()), Direction.values().length);
            ThermalMelting.meltNext(level, collisionPos, cursor);
        }
        return true;
    }

    static boolean isAuthority(BlockPos first, BlockPos second) {
        if (first.getX() != second.getX()) return first.getX() < second.getX();
        if (first.getY() != second.getY()) return first.getY() < second.getY();
        return first.getZ() < second.getZ();
    }

    private static ChemicalCloudBlockEntity cloud(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud ? cloud : null;
    }
}
