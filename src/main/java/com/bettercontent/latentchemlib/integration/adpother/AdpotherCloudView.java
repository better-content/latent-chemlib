package com.bettercontent.latentchemlib.integration.adpother;

import com.endertech.minecraft.mods.adpother.AdPother;
import com.endertech.minecraft.mods.adpother.blocks.AbstractGas;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.entities.PurifiedAir;
import com.bettercontent.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.bettercontent.latentchemlib.sim.ChemicalState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Read-only projection of loaded Latent clouds into AdPother's selector model.
 * This does not create AdPother gas blocks or maintain a second simulation index.
 */
public final class AdpotherCloudView {
    public static final AdpotherCloudView INSTANCE = new AdpotherCloudView();

    private AdpotherCloudView() {}

    public Optional<Pollutant<?>> selectorFor(ChemicalState state) {
        return state != null && state.isPure()
            ? AdpotherAtmosphereBridge.INSTANCE.pollutantFor(state.chemicalId()) : Optional.empty();
    }

    public Optional<AbstractGas> gasSelectorAt(ServerLevel level, BlockPos pos) {
        return loadedCloudAt(level, pos).flatMap(cloud ->
            AdpotherAtmosphereBridge.INSTANCE.pollutantById(cloud.pollutantState().pollutantId())
            .filter(AbstractGas.class::isInstance)
            .map(AbstractGas.class::cast));
    }

    public Optional<Contact> contactAt(ServerLevel level, BlockPos pos, Vec3 samplePosition) {
        return loadedCloudAt(level, pos).flatMap(cloud ->
            AdpotherAtmosphereBridge.INSTANCE.pollutantById(cloud.pollutantState().pollutantId()).flatMap(selector -> {
            int units = cloud.pollutantState().units();
            if (units <= 0) return Optional.empty();
            float protectedFraction = (float) level.getEntitiesOfClass(
                    PurifiedAir.class,
                    new AABB(samplePosition, samplePosition).inflate(32.0)
                ).stream()
                .filter(air -> air.getPollutant().map(selector::equals).orElse(false))
                .mapToDouble(air -> air.getConcentrationAt(samplePosition).toFraction())
                .sum();
            return Optional.of(new Contact(
                selector,
                com.bettercontent.latentchemlib.sim.GasHazardMath.attenuateUnits(units, protectedFraction)
            ));
        })).filter(contact -> contact.units() > 0);
    }

    private Optional<ChemicalCloudBlockEntity> loadedCloudAt(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return Optional.empty();
        if (!(chunk.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) return Optional.empty();
        return Optional.of(cloud);
    }

    public Detection detectionAround(ServerLevel level, BlockPos center, int radius) {
        LatentGasHazardService.Detection detection =
            LatentGasHazardService.INSTANCE.detectAround(level, center, radius);
        return new Detection(detection.explosionRisk(), detection.gasBlocks());
    }

    public record Contact(Pollutant<?> selector, int units) {}
    public record Detection(boolean explosionRisk, int gasBlocks) {}
}
