package com.gerald.latentchemlib.data;

import com.gerald.latentchemlib.sim.ChemicalState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public record NuclearDecayRule(
    String id,
    String inputChemical,
    String outputChemical,
    String outputItem,
    String isotope,
    double halfLifeSeconds,
    double outputMassRatio,
    double temperatureDelta,
    double chargeDelta,
    double energyDelta,
    float heatEmission
) {
    public boolean matches(ChemicalState state) {
        return halfLifeSeconds > 0.0 && state.massOf(inputChemical) > 0.0;
    }

    public double decayProbability(double elapsedSeconds) {
        if (halfLifeSeconds <= 0.0 || elapsedSeconds <= 0.0) return 0.0;
        double halfLives = elapsedSeconds / halfLifeSeconds;
        if (halfLives >= 64.0) return 1.0;
        return Math.max(0.0, Math.min(1.0, -Math.expm1(-Math.log(2.0) * halfLives)));
    }

    public ChemicalState apply(ChemicalState state) {
        String product = outputChemical == null || outputChemical.isBlank() ? inputChemical : outputChemical;
        return state.transmute(inputChemical, product, outputMassRatio).withConditions(
            Math.max(90.0, state.temperature() + temperatureDelta),
            Math.max(0.0, state.charge() + chargeDelta),
            Math.max(0.0, state.energy() + energyDelta)
        );
    }

    public Item outputItemValue() {
        if (outputItem == null || outputItem.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(outputItem);
        return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }

    public Item outputChemicalItemValue() {
        if (outputChemical == null || outputChemical.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(outputChemical);
        return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }
}
