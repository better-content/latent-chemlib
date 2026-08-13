package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.ChemicalTraits;
import com.bettercontent.latentchemlib.data.NuclearDecayRule;
import com.bettercontent.latentchemlib.data.NuclearPhenomenaProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Pure mass/energy ledger shared by ambient and contained emergent phenomena. */
public final class NuclearPhenomenaMath {
    private static final double MASS_ENERGY_UNITS = 1_000_000.0;

    private NuclearPhenomenaMath() {}

    public static Optional<FissionResult> fission(
        ChemicalState input,
        double neutronFlux,
        double moderation,
        double contactFraction,
        NuclearPhenomenaProfile profile,
        Predicate<String> fissileEvidence
    ) {
        if (input.mass() <= 0.0 || input.density() < profile.fissionMinimumDensity()
            || input.temperature() < profile.fissionMinimumTemperature()
            || neutronFlux < profile.fissionMinimumNeutronFlux()
            || moderation < profile.fissionMinimumModeration()
            || contactFraction < profile.fissionMinimumContactFraction()) {
            return Optional.empty();
        }
        String fuel = input.components().keySet().stream()
            .filter(fissileEvidence)
            .filter(id -> input.massOf(id) >= profile.fissionMinimumFuelMass())
            .filter(id -> input.massOf(id) / input.mass() >= profile.fissionMinimumFuelFraction())
            .findFirst()
            .orElse("");
        if (fuel.isBlank()) return Optional.empty();
        double fuelMass = input.massOf(fuel);

        double consumed = Math.min(fuelMass, profile.fissionBatchMass());
        ChemicalState.Split split = input.splitChemical(fuel, consumed);
        double defect = consumed * profile.fissionMassDefectFraction();
        double defectEnergy = defect * MASS_ENERGY_UNITS;
        float emittedHeat = (float) Math.min(profile.fissionHeatEmission(), defectEnergy);
        double daughterMass = consumed - defect;
        double primary = daughterMass * profile.fissionPrimaryDaughterFraction();
        double secondary = daughterMass - primary;
        Map<String, Double> daughters = new LinkedHashMap<>();
        daughters.put(profile.fissionPrimaryDaughter(), primary);
        daughters.put(profile.fissionSecondaryDaughter(), secondary);
        ChemicalState products = new ChemicalState(
            daughters,
            split.extracted().density() * daughterMass / consumed,
            Math.max(input.temperature(), 2_400.0),
            Math.max(input.charge(), 0.35),
            split.extracted().energy() + defectEnergy - emittedHeat
        );
        return Optional.of(new FissionResult(split.remainder().merge(products), consumed, primary, secondary, defect, emittedHeat));
    }

    /** Deterministic ensemble decay: transformed mass plus emitted energy equals input mass-energy. */
    public static Optional<DecayHeatResult> continuousDecay(
        ChemicalState input,
        NuclearDecayRule rule,
        double elapsedSeconds,
        NuclearPhenomenaProfile profile
    ) {
        double available = input.massOf(rule.inputChemical()) * input.explicitIsotopesOf(rule.inputChemical())
            .map(ensemble -> ensemble.isNatural() ? 1.0 : ensemble.fraction(rule.isotopeMassNumber()))
            .orElse(1.0);
        double outputRatio = Math.max(0.0, Math.min(1.0, rule.outputMassRatio()));
        double defectFraction = 1.0 - outputRatio;
        if (available <= 0.0 || elapsedSeconds <= 0.0 || rule.halfLifeSeconds() <= 0.0
            || rule.heatEmission() <= 0.0f || defectFraction <= 0.0) return Optional.empty();
        double logHalfLife = Math.log10(1.0 + rule.halfLifeSeconds());
        double instability = 1.0 / (1.0 + logHalfLife / profile.decayHalfLifeLogScale());
        double requestedHeat = rule.heatEmission()
            * (available / profile.decayReferenceMass()) * instability * elapsedSeconds;
        requestedHeat = Math.max(requestedHeat, available * profile.decayMinimumSpecificHeatPerSecond() * elapsedSeconds);
        double maximumHeat = available * defectFraction * MASS_ENERGY_UNITS;
        double heat = Math.min(requestedHeat, maximumHeat);
        double defect = heat / MASS_ENERGY_UNITS;
        double consumed = Math.min(available, defect / defectFraction);
        double daughter = consumed * outputRatio;
        if (consumed <= 0.0 || daughter <= 0.0 || defect <= 0.0) return Optional.empty();

        Map<String, Double> components = new LinkedHashMap<>(input.components());
        double remaining = available - consumed;
        if (remaining > 0.0) components.put(rule.inputChemical(), remaining); else components.remove(rule.inputChemical());
        components.merge(rule.outputChemical(), daughter, Double::sum);
        double outputMass = input.mass() - defect;
        ChemicalState output = new ChemicalState(
            components,
            input.density() * outputMass / input.mass(),
            input.temperature(), input.charge(), input.energy()
        );
        output = ChemicalState.withDecayIdentity(
            input, output, rule.inputChemical(), rule.isotopeMassNumber(), consumed,
            rule.outputChemical(), rule.daughterIsotopeMassNumber(), daughter
        );
        return Optional.of(new DecayHeatResult(output, consumed, daughter, defect, (float) heat));
    }

    public static Optional<FusionResult> fusion(
        ChemicalState first,
        ChemicalState second,
        ChemicalTraits traits,
        boolean geometricallyOpposed,
        NuclearPhenomenaProfile profile
    ) {
        if (!geometricallyOpposed || !isFusionStreamCandidate(first, profile) || !isFusionStreamCandidate(second, profile)) return Optional.empty();
        if (!EmergentMath.fusionIntercept(first, second, traits, 2, 2.0)) {
            return Optional.empty();
        }
        double firstMass = Math.min(first.mass(), profile.fusionBatchMassPerStream());
        double secondMass = Math.min(second.mass(), profile.fusionBatchMassPerStream());
        ChemicalState.Split firstSplit = first.split(firstMass);
        ChemicalState.Split secondSplit = second.split(secondMass);
        double consumed = firstMass + secondMass;
        double defect = consumed * profile.fusionMassDefectFraction();
        double defectEnergy = defect * MASS_ENERGY_UNITS;
        float emittedHeat = (float) Math.min(profile.fusionHeatEmission(), defectEnergy);
        ChemicalState helium = new ChemicalState(
            "chemlib:helium", consumed - defect,
            firstSplit.extracted().density() + secondSplit.extracted().density(),
            Math.max(first.temperature(), second.temperature()) + 1_500.0,
            Math.max(first.charge(), second.charge()),
            firstSplit.extracted().energy() + secondSplit.extracted().energy() + defectEnergy - emittedHeat
        );
        return Optional.of(new FusionResult(firstSplit.remainder(), secondSplit.remainder(), helium, consumed, defect, emittedHeat));
    }

    private static boolean compatibleHydrogen(ChemicalState state) {
        if (state == null || !state.isPure()) return false;
        return state.contains("chemlib:hydrogen") || state.contains("chemlib:deuterium") || state.contains("chemlib:tritium");
    }

    public static boolean isFusionStreamCandidate(ChemicalState state, NuclearPhenomenaProfile profile) {
        return compatibleHydrogen(state)
            && state.mass() >= profile.fusionBatchMassPerStream()
            && state.temperature() >= profile.fusionMinimumTemperature()
            && state.density() >= profile.fusionMinimumDensity()
            && state.energy() >= profile.fusionMinimumEnergy();
    }

    public record FissionResult(
        ChemicalState output,
        double consumedMass,
        double primaryMass,
        double secondaryMass,
        double massDefect,
        float heatEmission
    ) {}

    public record DecayHeatResult(ChemicalState output, double consumedMass, double daughterMass, double massDefect, float heatEmission) {}

    public record FusionResult(
        ChemicalState firstRemainder,
        ChemicalState secondRemainder,
        ChemicalState product,
        double consumedMass,
        double massDefect,
        float heatEmission
    ) {}
}
