package com.bettercontent.latentchemlib.integration.adpother;

import com.endertech.minecraft.mods.adpother.AdPother;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.bettercontent.latentchemlib.sim.CloudInsertionService;
import com.bettercontent.latentchemlib.sim.CloudExtractionService;
import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

public final class AdpotherAtmosphereBridge {
    public static final AdpotherAtmosphereBridge INSTANCE = new AdpotherAtmosphereBridge();

    private AdpotherAtmosphereBridge() {}

    public int emit(Pollutant<?> pollutant, LevelAccessor level, BlockPos pos, int units) {
        if (!(level instanceof ServerLevel serverLevel) || units <= 0) return 0;
        return CloudInsertionService.INSTANCE.insertPollutantUnits(serverLevel, pos, pollutant, units).acceptedUnits();
    }

    public int extract(Pollutant<?> pollutant, LevelAccessor level, BlockPos pos, int units) {
        if (!(level instanceof ServerLevel serverLevel) || units <= 0) return 0;
        return CloudExtractionService.INSTANCE.extractPollutantUnits(serverLevel, pos, pollutantId(pollutant), units);
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

    public String pollutantId(Pollutant<?> pollutant) {
        return pollutant == null ? "" : pollutant.getSimpleName();
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

    public Optional<Pollutant<?>> pollutantById(String pollutantId) {
        if (pollutantId == null || pollutantId.isBlank()) return Optional.empty();
        return AdPother.getInstance().pollutants.findByName(pollutantId);
    }
}
