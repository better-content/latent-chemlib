package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import com.bettercontent.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherAtmosphereBridge;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Atomic boundary between contained chemical mass and bounded AdPother pollutant cells. */
public final class CloudInsertionService {
    public static final CloudInsertionService INSTANCE = new CloudInsertionService();
    public static final double MASS_PER_ADPOTHER_UNIT = 16.0;

    private CloudInsertionService() {}

    public InsertionResult insert(ServerLevel level, BlockPos origin, ChemicalState state) {
        if (state == null || state.mass() <= 0.0) return InsertionResult.rejected(state == null ? ChemicalState.empty() : state);
        List<PollutantPayload> payloads = new ArrayList<>();
        for (var component : state.components().entrySet()) {
            int units = (int) Math.floor(component.getValue() / MASS_PER_ADPOTHER_UNIT);
            if (units <= 0) continue; // Deliberately rounded down and discarded at release.
            Pollutant<?> pollutant = AdpotherAtmosphereBridge.INSTANCE.pollutantFor(component.getKey()).orElse(null);
            if (pollutant == null) return InsertionResult.rejected(state);
            payloads.add(new PollutantPayload(pollutant, units));
        }
        double acceptedMass = 0.0;
        BlockPos firstTarget = null;
        for (PollutantPayload payload : payloads) {
            UnitInsertion inserted = insertPollutantUnits(level, origin, payload.pollutant(), payload.units());
            acceptedMass += inserted.acceptedUnits() * MASS_PER_ADPOTHER_UNIT;
            if (firstTarget == null) firstTarget = inserted.firstTarget();
            if (inserted.acceptedUnits() != payload.units()) return new InsertionResult(acceptedMass, state.mass() - acceptedMass, firstTarget);
        }
        return new InsertionResult(acceptedMass, 0.0, firstTarget);
    }

    public UnitInsertion insertPollutantUnits(ServerLevel level, BlockPos origin, Pollutant<?> pollutant, int requestedUnits) {
        int remaining = Math.max(0, requestedUnits);
        if (remaining == 0 || pollutant == null) return new UnitInsertion(0, null);
        String pollutantId = AdpotherAtmosphereBridge.INSTANCE.pollutantId(pollutant);
        List<BlockPos> candidates = candidateOffsets(AdpotherAtmosphereBridge.INSTANCE.chemicalId(pollutant)).stream()
            .map(origin::offset).map(BlockPos::immutable)
            .filter(level::isInWorldBounds).filter(level::isLoaded).toList();
        int available = 0;
        int capacity = Math.max(1, pollutant.getPollutionCapacity());
        for (BlockPos pos : candidates) {
            if (level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud) {
                if (cloud.pollutantState().pollutantId().equals(pollutantId)) available += Math.max(0, capacity - cloud.pollutantState().units());
            } else if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                available += capacity;
            }
            if (available >= remaining) break;
        }
        if (available < remaining) return new UnitInsertion(0, null);

        BlockPos first = null;
        int accepted = 0;
        for (BlockPos pos : candidates) {
            ChemicalCloudBlockEntity cloud = level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity found ? found : null;
            if (cloud != null && !cloud.pollutantState().pollutantId().equals(pollutantId)) continue;
            if (cloud == null) {
                if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) continue;
                if (!level.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3)) continue;
                cloud = level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity found ? found : null;
            }
            if (cloud == null) continue;
            int inserted = cloud.insertUnits(pollutantId, remaining);
            if (inserted > 0 && first == null) first = pos;
            accepted += inserted;
            remaining -= inserted;
            if (remaining == 0) break;
        }
        return new UnitInsertion(accepted, first);
    }

    /** Inserts only into the addressed cell; used by deterministic atmospheric movement. */
    public int insertAt(ServerLevel level, BlockPos pos, Pollutant<?> pollutant, int requestedUnits) {
        if (pollutant == null || requestedUnits <= 0 || !level.isInWorldBounds(pos) || !level.isLoaded(pos)) return 0;
        String pollutantId = AdpotherAtmosphereBridge.INSTANCE.pollutantId(pollutant);
        ChemicalCloudBlockEntity cloud = level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity found ? found : null;
        if (cloud != null && !cloud.pollutantState().pollutantId().equals(pollutantId)) return 0;
        if (cloud == null) {
            if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) return 0;
            if (!level.setBlock(pos, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3)) return 0;
            cloud = level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity found ? found : null;
        }
        return cloud == null ? 0 : cloud.insertUnits(pollutantId, requestedUnits);
    }

    static List<BlockPos> candidateOffsets(String chemicalId) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) offsets.add(new BlockPos(x, y, z));
        int chemicalHash = chemicalId.hashCode();
        offsets.sort(Comparator.comparingInt((BlockPos pos) -> pos.distManhattan(BlockPos.ZERO))
            .thenComparingInt(pos -> Integer.rotateLeft(pos.hashCode() ^ chemicalHash, 13))
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    public record UnitInsertion(int acceptedUnits, BlockPos firstTarget) {}
    private record PollutantPayload(Pollutant<?> pollutant, int units) {}
    public record InsertionResult(double acceptedMass, double rejectedMass, BlockPos target) {
        static InsertionResult rejected(ChemicalState state) { return new InsertionResult(0.0, Math.max(0.0, state.mass()), null); }
        public boolean acceptedAll() { return acceptedMass > 0.0 && rejectedMass == 0.0; }
    }
}
