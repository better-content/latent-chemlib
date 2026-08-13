package com.bettercontent.latentchemlib.data;

import com.bettercontent.latentchemlib.sim.ChemicalState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public record ReactionRule(
    String id,
    String inputChemical,
    String outputChemical,
    String outputItem,
    double minMass,
    double minTemperature,
    double minCharge,
    double minEnergy,
    double outputMassRatio,
    double temperatureDelta,
    double chargeDelta,
    double energyDelta,
    float heatCost,
    float heatEmission
) {
    public boolean matches(ChemicalState state, float availableHeat) {
        if (state.massOf(inputChemical) < minMass) return false;
        if (state.temperature() < minTemperature) return false;
        if (state.charge() < minCharge) return false;
        if (state.energy() < minEnergy) return false;
        return availableHeat >= heatCost;
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
}
