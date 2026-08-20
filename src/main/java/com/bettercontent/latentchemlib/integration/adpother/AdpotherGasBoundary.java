package com.bettercontent.latentchemlib.integration.adpother;

import com.bettercontent.latentchemlib.sim.ChemicalState;
import com.endertech.minecraft.mods.adpother.AdPother;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The narrow boundary between contained Latent chemistry and AdPother's native
 * atmospheric blocks. Once matter crosses this boundary, AdPother is its sole
 * simulation authority.
 */
public final class AdpotherGasBoundary {
    public static final AdpotherGasBoundary INSTANCE = new AdpotherGasBoundary();
    public static final double MASS_PER_ADPOTHER_UNIT = 16.0;

    private AdpotherGasBoundary() {}

    /** Atomically releases every whole unit represented by the supplied contained state. */
    public ReleaseResult release(ServerLevel level, BlockPos origin, ChemicalState state) {
        if (state == null || state.mass() <= 0.0) return ReleaseResult.rejected(state);

        List<PollutantPayload> payloads = new ArrayList<>();
        for (var component : state.components().entrySet()) {
            int units = (int) Math.floor(component.getValue() / MASS_PER_ADPOTHER_UNIT);
            if (units <= 0) continue;
            Pollutant<?> pollutant = pollutantFor(component.getKey()).orElse(null);
            if (pollutant == null) return ReleaseResult.rejected(state);
            payloads.add(new PollutantPayload(component.getKey(), pollutant, units));
        }
        if (payloads.isEmpty()) return ReleaseResult.rejected(state);

        Map<BlockPos, BlockState> virtualStates = new HashMap<>();
        List<Placement> placements = new ArrayList<>();
        for (PollutantPayload payload : payloads) {
            int remaining = payload.units();
            for (BlockPos offset : candidateOffsets(payload.chemicalId())) {
                if (remaining == 0) break;
                BlockPos pos = origin.offset(offset).immutable();
                if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) continue;
                BlockState simulated = virtualStates.computeIfAbsent(pos, level::getBlockState);
                int accepted = 0;
                while (remaining > 0 && payload.pollutant().canStateBePumped(simulated)) {
                    BlockState next = payload.pollutant().getPumpedState(simulated);
                    if (next.equals(simulated)) break;
                    simulated = next;
                    accepted++;
                    remaining--;
                }
                if (accepted > 0) {
                    virtualStates.put(pos, simulated);
                    placements.add(new Placement(pos, payload.pollutant(), accepted));
                }
            }
            if (remaining > 0) return ReleaseResult.rejected(state);
        }

        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        BlockPos firstTarget = null;
        int acceptedUnits = 0;
        for (Placement placement : placements) {
            originals.putIfAbsent(placement.pos(), level.getBlockState(placement.pos()));
            int inserted = placement.pollutant().pump(level, placement.pos(), placement.units());
            if (inserted != placement.units()) {
                originals.forEach((pos, original) -> level.setBlock(pos, original, 3));
                return ReleaseResult.rejected(state);
            }
            if (firstTarget == null) firstTarget = placement.pos();
            acceptedUnits += inserted;
        }
        return new ReleaseResult(
            acceptedUnits * MASS_PER_ADPOTHER_UNIT,
            0.0,
            firstTarget
        );
    }

    /** Removes native pollution from one cell and returns its contained representation. */
    public ChemicalState capture(ServerLevel level, BlockPos pos, int requestedUnits) {
        int requested = Math.max(0, requestedUnits);
        if (requested == 0 || !level.isLoaded(pos)) return ChemicalState.empty();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof Pollutant<?> pollutant)) return ChemicalState.empty();
        int extracted = pollutant.spend(level, pos, requested);
        if (extracted <= 0) return ChemicalState.empty();
        return new ChemicalState(
            chemicalId(pollutant),
            extracted * MASS_PER_ADPOTHER_UNIT,
            extracted,
            293.0,
            0.0,
            0.0
        );
    }

    public String chemicalId(Pollutant<?> pollutant) {
        ResourceLocation pollutantId = ForgeRegistries.BLOCKS.getKey(pollutant);
        String path = pollutantId == null ? pollutant.getSimpleName() : pollutantId.getPath();
        if ("carbon".equals(path)) return "chemlib:carbon_dioxide";
        if ("sulfur".equals(path)) return "chemlib:sulfur_dioxide";
        if ("dust".equals(path)) return "latent_chemlib:dust";

        ResourceLocation chemicalId = new ResourceLocation("chemlib", path);
        if (ForgeRegistries.ITEMS.getValue(chemicalId) instanceof Chemical chemical
            && chemical.getMatterState() == MatterState.GAS) {
            return chemicalId.toString();
        }
        return "adpother:" + path;
    }

    public Optional<Pollutant<?>> pollutantFor(String chemicalId) {
        if (chemicalId == null || chemicalId.isBlank()) return Optional.empty();
        int separator = chemicalId.indexOf(':');
        String path = separator >= 0 ? chemicalId.substring(separator + 1) : chemicalId;
        if (path.endsWith("_lamp_block")) path = path.substring(0, path.length() - "_lamp_block".length());
        Optional<Pollutant<?>> exact = AdPother.getInstance().pollutants.findByName(path);
        if (exact.isPresent()) return exact;
        if ("carbon_dioxide".equals(path)) return AdPother.getInstance().pollutants.findByName("carbon");
        if ("sulfur_dioxide".equals(path)) return AdPother.getInstance().pollutants.findByName("sulfur");
        return Optional.empty();
    }

    static List<BlockPos> candidateOffsets(String chemicalId) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) offsets.add(new BlockPos(x, y, z));
            }
        }
        int chemicalHash = chemicalId.hashCode();
        offsets.sort(Comparator.comparingInt((BlockPos pos) -> pos.distManhattan(BlockPos.ZERO))
            .thenComparingInt(pos -> Integer.rotateLeft(pos.hashCode() ^ chemicalHash, 13))
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    private record PollutantPayload(String chemicalId, Pollutant<?> pollutant, int units) {}
    private record Placement(BlockPos pos, Pollutant<?> pollutant, int units) {}

    public record ReleaseResult(double acceptedMass, double rejectedMass, BlockPos target) {
        static ReleaseResult rejected(ChemicalState state) {
            return new ReleaseResult(0.0, state == null ? 0.0 : Math.max(0.0, state.mass()), null);
        }

        public boolean acceptedAll() {
            return acceptedMass > 0.0 && rejectedMass == 0.0;
        }
    }
}
