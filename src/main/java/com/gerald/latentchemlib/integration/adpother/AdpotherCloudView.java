package com.gerald.latentchemlib.integration.adpother;

import com.endertech.minecraft.mods.adpother.AdPother;
import com.endertech.minecraft.mods.adpother.blocks.AbstractGas;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.CloudInsertionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only projection of loaded Latent clouds into AdPother's selector model.
 * This does not create AdPother gas blocks or maintain a second simulation index.
 */
public final class AdpotherCloudView {
    public static final AdpotherCloudView INSTANCE = new AdpotherCloudView();

    private AdpotherCloudView() {}

    public Optional<Pollutant<?>> selectorFor(ChemicalState state) {
        String id = state.chemicalId();
        int separator = id.indexOf(':');
        String path = separator >= 0 ? id.substring(separator + 1) : id;
        if ("dust".equals(path)) path = "dust";
        Optional<Pollutant<?>> exact = AdPother.getInstance().pollutants.findByName(path);
        if (exact.isPresent()) return exact;
        if ("carbon_dioxide".equals(path)) return AdPother.getInstance().pollutants.findByName("carbon");
        if ("sulfur_dioxide".equals(path)) return AdPother.getInstance().pollutants.findByName("sulfur");
        return Optional.empty();
    }

    public Optional<AbstractGas> gasSelectorAt(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) return Optional.empty();
        return selectorFor(cloud.chemicalState())
            .filter(AbstractGas.class::isInstance)
            .map(AbstractGas.class::cast);
    }

    public BlockState projectedState(ServerLevel level, BlockPos pos, BlockState fallback) {
        return gasSelectorAt(level, pos).map(AbstractGas::defaultBlockState).orElse(fallback);
    }

    public Map<Pollutant<?>, Integer> quantitiesAround(ServerLevel level, BlockPos center, int chunkRadius) {
        Map<Pollutant<?>, Double> massBySelector = new HashMap<>();
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ChemicalCloudBlockEntity cloud)) continue;
                    selectorFor(cloud.chemicalState()).ifPresent(selector ->
                        massBySelector.merge(selector, cloud.chemicalState().mass(), Double::sum)
                    );
                }
            }
        }
        Map<Pollutant<?>, Integer> quantities = new HashMap<>();
        massBySelector.forEach((selector, mass) -> {
            int units = (int) Math.floor(mass / CloudInsertionService.MASS_PER_ADPOTHER_UNIT);
            if (units > 0) quantities.put(selector, units);
        });
        return quantities;
    }

    public Detection detectionAround(ServerLevel level, BlockPos center, int radius) {
        int gasBlocks = 0;
        boolean explosionRisk = false;
        int chunkRadius = Math.max(1, (radius + 15) / 16);
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        double radiusSquared = (double) radius * radius;
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) continue;
                for (BlockEntity blockEntity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (!(blockEntity instanceof ChemicalCloudBlockEntity cloud)
                        || blockEntity.getBlockPos().distSqr(center) > radiusSquared) continue;
                    int units = (int) Math.floor(
                        cloud.chemicalState().mass() / CloudInsertionService.MASS_PER_ADPOTHER_UNIT
                    );
                    gasBlocks += Math.max(1, units);
                    explosionRisk |= selectorFor(cloud.chemicalState())
                        .map(selector -> selector.getPollutionCapacity() > 0
                            && selector.defaultBlockState().getBlock() instanceof AbstractGas gas
                            && gas.getLowerExplosiveLimit() > 0)
                        .orElse(false);
                }
            }
        }
        return new Detection(explosionRisk, gasBlocks);
    }

    public record Detection(boolean explosionRisk, int gasBlocks) {}
}
