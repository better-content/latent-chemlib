package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.ChemicalTraits;
import com.bettercontent.latentchemlib.data.NuclearDecayRule;
import com.bettercontent.latentchemlib.data.NuclearPhenomenaProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NuclearPhenomenaMathTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void fissionNeedsConcentrationThermalFluxModerationAndContactTogether() {
        ChemicalState fuel = new ChemicalState("chemlib:uranium", 1_000.0, 8.0, 900.0, 0.0, 0.0);
        NuclearPhenomenaProfile profile = NuclearPhenomenaProfile.defaults();

        assertTrue(NuclearPhenomenaMath.fission(fuel, 40_000.0, 0.0, 0.5, profile, id -> true).isEmpty());
        assertTrue(NuclearPhenomenaMath.fission(fuel, 40_000.0, 0.35, 0.0, profile, id -> true).isEmpty());
        assertTrue(NuclearPhenomenaMath.fission(fuel, 40_000.0, 0.35, 0.5, profile, id -> true).isPresent());
    }

    @Test
    void fissionConservesSpectatorsAndBothDaughtersWithExplicitMassDefect() {
        ChemicalState input = new ChemicalState("chemlib:uranium", 1_000.0, 8.0, 900.0, 0.0, 0.0)
            .merge(new ChemicalState("chemlib:argon", 100.0, 1.0, 900.0, 0.0, 0.0));

        var result = NuclearPhenomenaMath.fission(
            input, 40_000.0, 0.35, 0.5, NuclearPhenomenaProfile.defaults(), id -> id.equals("chemlib:uranium")
        ).orElseThrow();

        assertEquals(100.0, result.output().massOf("chemlib:argon"), EPSILON);
        assertTrue(result.output().massOf("chemlib:barium") > 0.0);
        assertTrue(result.output().massOf("chemlib:krypton") > 0.0);
        assertEquals(input.mass(), result.output().mass() + result.massDefect(), EPSILON);
        assertEquals(result.consumedMass(), result.primaryMass() + result.secondaryMass() + result.massDefect(), EPSILON);
        assertEquals(input.mass() * 1_000_000.0 + input.energy(),
            result.output().mass() * 1_000_000.0 + result.output().energy() + result.heatEmission(), 1.0e-6);
    }

    @Test
    void qualifyingConfiguredFuelIsSelectedInsteadOfTraceNonqualifyingSpecies() {
        ChemicalState input = new ChemicalState("chemlib:uranium", 1.0, 0.01, 900.0, 0.0, 0.0)
            .merge(new ChemicalState("chemlib:californium", 1_000.0, 8.0, 900.0, 0.0, 0.0));

        var result = NuclearPhenomenaMath.fission(
            input, 40_000.0, 0.35, 0.5, NuclearPhenomenaProfile.defaults(),
            id -> id.equals("chemlib:californium")
        ).orElseThrow();

        assertEquals(1.0, result.output().massOf("chemlib:uranium"), EPSILON);
        assertTrue(result.output().massOf("chemlib:californium") < 1_000.0);
    }

    @Test
    void continuousDecayConvertsConfiguredDaughterAndConservesMassEnergy() {
        ChemicalState input = new ChemicalState("chemlib:bismuth", 1_000.0, 8.0, 600.0, 0.0, 0.0)
            .withPureIsotope("chemlib:bismuth", 209);
        NuclearDecayRule rule = new NuclearDecayRule(
            "test:bismuth", "chemlib:bismuth", "chemlib:thallium", "", "Bi-209", "Tl-205",
            6.344e26, 0.980861244, 0.0, 0.0, 0.0, 120.0f
        );

        var result = NuclearPhenomenaMath.continuousDecay(
            input, rule, 1.0, NuclearPhenomenaProfile.defaults()
        ).orElseThrow();

        assertTrue(result.daughterMass() > 0.0);
        assertTrue(result.heatEmission() > 0.0f);
        assertEquals(4_000.0, result.heatEmission(), 1.0e-3, "Configured minimum specific power must be gameplay-significant");
        assertEquals(input.mass(), result.output().mass() + result.massDefect(), EPSILON);
        assertEquals(result.consumedMass(), result.daughterMass() + result.massDefect(), EPSILON);
        assertEquals(result.massDefect() * 1_000_000.0, result.heatEmission(), 1.0e-3);
        assertEquals(1.0, result.output().isotopesOf("chemlib:bismuth").fraction(209));
        assertEquals(1.0, result.output().isotopesOf("chemlib:thallium").fraction(205));
    }

    @Test
    void explicitMismatchedIsotopeCannotEnterAnotherIsotopesDecayRule() {
        ChemicalState polonium218 = new ChemicalState("chemlib:polonium", 218.0, 1.0, 293.0, 0.0, 0.0)
            .withPureIsotope("chemlib:polonium", 218);
        NuclearDecayRule polonium209 = new NuclearDecayRule(
            "test:po209", "chemlib:polonium", "chemlib:lead", "", "Po-209", "Pb-205",
            10.0, 205.0 / 209.0, 0.0, 0.0, 0.0, 10.0f
        );

        assertTrue(NuclearPhenomenaMath.continuousDecay(
            polonium218, polonium209, 1.0, NuclearPhenomenaProfile.defaults()
        ).isEmpty());
    }

    @Test
    void fusionRequiresActuallyOpposedHotDenseEnergeticCompatibleStreams() {
        ChemicalState hot = new ChemicalState("chemlib:hydrogen", 16.0, 8.0, 9_000.0, 2.0, 80_000.0);
        ChemicalState cold = new ChemicalState("chemlib:hydrogen", 16.0, 8.0, 300.0, 0.0, 80_000.0);
        var profile = NuclearPhenomenaProfile.defaults();

        assertTrue(NuclearPhenomenaMath.fusion(hot, hot, ChemicalTraits.fallback(), false, profile).isEmpty());
        assertTrue(NuclearPhenomenaMath.fusion(hot, cold, ChemicalTraits.fallback(), true, profile).isEmpty());
        var result = NuclearPhenomenaMath.fusion(hot, hot, ChemicalTraits.fallback(), true, profile).orElseThrow();

        assertEquals("chemlib:helium", result.product().chemicalId());
        assertEquals(result.consumedMass(), result.product().mass() + result.massDefect(), EPSILON);
        assertEquals(hot.mass() * 2.0,
            result.firstRemainder().mass() + result.secondRemainder().mass() + result.product().mass() + result.massDefect(), EPSILON);
        assertEquals(hot.mass() * 2.0 * 1_000_000.0 + hot.energy() * 2.0,
            (result.firstRemainder().mass() + result.secondRemainder().mass() + result.product().mass()) * 1_000_000.0
                + result.firstRemainder().energy() + result.secondRemainder().energy() + result.product().energy() + result.heatEmission(),
            1.0e-6);
    }
}
