package com.bettercontent.latentchemlib.data;

import com.bettercontent.latentchemlib.sim.ChemicalState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public record NuclearDecayRule(
    String id,
    String inputChemical,
    String outputChemical,
    String outputItem,
    String isotope,
    String outputIsotope,
    double halfLifeSeconds,
    double outputMassRatio,
    double temperatureDelta,
    double chargeDelta,
    double energyDelta,
    float heatEmission
) {
    public NuclearDecayRule(
        String id, String inputChemical, String outputChemical, String outputItem, String isotope,
        double halfLifeSeconds, double outputMassRatio, double temperatureDelta, double chargeDelta,
        double energyDelta, float heatEmission
    ) {
        this(id, inputChemical, outputChemical, outputItem, isotope, "", halfLifeSeconds, outputMassRatio,
            temperatureDelta, chargeDelta, energyDelta, heatEmission);
    }

    public int isotopeMassNumber() {
        return parseMassNumber(isotope);
    }

    /** Explicit daughter identity, with a migration fallback for older datapacks. */
    public int daughterIsotopeMassNumber() {
        int explicit = parseMassNumber(outputIsotope);
        if (explicit > 0) return explicit;
        int parent = isotopeMassNumber();
        return parent <= 0 ? 0 : Math.max(1, (int) Math.round(parent * Math.max(0.0, outputMassRatio)));
    }

    private static int parseMassNumber(String isotopeName) {
        if (isotopeName == null || isotopeName.isBlank()) return 0;
        int separator = isotopeName.lastIndexOf('-');
        String value = separator >= 0 ? isotopeName.substring(separator + 1) : isotopeName.replaceAll("\\D+", "");
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean matches(ChemicalState state) {
        if (halfLifeSeconds <= 0.0 || state.massOf(inputChemical) <= 0.0) return false;
        return state.explicitIsotopesOf(inputChemical)
            .map(ensemble -> ensemble.isNatural() || ensemble.fraction(isotopeMassNumber()) > 0.0)
            .orElse(true);
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
