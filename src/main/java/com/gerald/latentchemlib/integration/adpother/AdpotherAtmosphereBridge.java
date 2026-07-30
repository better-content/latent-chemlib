package com.gerald.latentchemlib.integration.adpother;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.CloudInsertionService;
import com.gerald.latentchemlib.sim.CloudExtractionService;
import com.smashingmods.chemlib.api.Chemical;
import com.smashingmods.chemlib.api.MatterState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

public final class AdpotherAtmosphereBridge {
    public static final AdpotherAtmosphereBridge INSTANCE = new AdpotherAtmosphereBridge();

    private AdpotherAtmosphereBridge() {}

    public int emit(Pollutant<?> pollutant, LevelAccessor level, BlockPos pos, int units) {
        if (!(level instanceof ServerLevel serverLevel) || units <= 0) return 0;
        String chemicalId = chemicalId(pollutant);
        AdpotherEmissionContext context = AdpotherEmissionContext.current().orElse(AdpotherEmissionContext.AMBIENT);
        ChemicalTraits traits = LatentDataManager.INSTANCE.traits(chemicalId);
        ChemicalState oneUnit = new ChemicalState(
            chemicalId,
            CloudInsertionService.MASS_PER_ADPOTHER_UNIT,
            Math.max(0.03, traits.volatility() * 0.18),
            context.temperature(),
            context.charge(),
            context.energyPerUnit()
        );
        return CloudInsertionService.INSTANCE.insertAdpotherUnits(serverLevel, pos, oneUnit, units);
    }

    public int extract(Pollutant<?> pollutant, LevelAccessor level, BlockPos pos, int units) {
        if (!(level instanceof ServerLevel serverLevel) || units <= 0) return 0;
        return CloudExtractionService.INSTANCE.extractAdpotherUnits(serverLevel, pos, chemicalId(pollutant), units);
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
}
