package com.gerald.latentchemlib.data;

import com.gerald.latentchemlib.sim.ChemicalState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NuclearDecayRuleTest {
    @Test
    void probabilityUsesRealHalfLifeCurve() {
        NuclearDecayRule rule = rule(10.0);

        assertEquals(0.0, rule.decayProbability(0.0));
        assertEquals(0.5, rule.decayProbability(10.0), 1.0e-12);
        assertEquals(0.75, rule.decayProbability(20.0), 1.0e-12);
        assertEquals(0.875, rule.decayProbability(30.0), 1.0e-12);
    }

    @Test
    void veryShortHalfLifeIsEffectivelyCertainAtOneSecondCadence() {
        NuclearDecayRule rule = rule(0.0007);

        assertEquals(1.0, rule.decayProbability(1.0));
    }

    @Test
    void veryLongHalfLifeStillReturnsNonZeroProbability() {
        NuclearDecayRule rule = rule(141_014_428_800_000_000.0);

        assertEquals(4.915434444960467e-18, rule.decayProbability(1.0), 1.0e-30);
    }

    @Test
    void stableOrInvalidRulesDoNotMatch() {
        NuclearDecayRule rule = rule(0.0);
        ChemicalState state = new ChemicalState("chemlib:uranium", 10.0, 1.0, 293.0, 0.0, 0.0);

        assertFalse(rule.matches(state));
        assertEquals(0.0, rule.decayProbability(1.0));
    }

    @Test
    void applyTransformsToDaughterWithoutNegativeState() {
        NuclearDecayRule rule = new NuclearDecayRule(
            "test",
            "chemlib:uranium",
            "chemlib:thorium",
            "",
            "U-238",
            10.0,
            0.5,
            100.0,
            -2.0,
            -500.0,
            0.0f
        );
        ChemicalState out = rule.apply(new ChemicalState("chemlib:uranium", 200.0, 4.0, 500.0, 1.0, 250.0));

        assertEquals("chemlib:thorium", out.chemicalId());
        assertEquals(100.0, out.mass());
        assertEquals(2.0, out.density());
        assertEquals(600.0, out.temperature());
        assertEquals(0.0, out.charge());
        assertEquals(0.0, out.energy());
    }

    @Test
    void applyFallsBackToInputWhenDaughterIsBlank() {
        NuclearDecayRule rule = new NuclearDecayRule(
            "test",
            "chemlib:uranium",
            "",
            "",
            "U-238",
            10.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0f
        );
        ChemicalState out = rule.apply(new ChemicalState("chemlib:uranium", 200.0, 4.0, 500.0, 1.0, 250.0));

        assertEquals("chemlib:uranium", out.chemicalId());
    }

    @Test
    void itemLookupsReturnNullForBlankOrInvalidIds() {
        NuclearDecayRule blank = new NuclearDecayRule(
            "test",
            "chemlib:uranium",
            "",
            "",
            "U-238",
            10.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0f
        );
        NuclearDecayRule invalid = new NuclearDecayRule(
            "test",
            "chemlib:uranium",
            "not a valid id",
            "not a valid id",
            "U-238",
            10.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0f
        );

        assertEquals(null, blank.outputItemValue());
        assertEquals(null, blank.outputChemicalItemValue());
        assertEquals(null, invalid.outputItemValue());
        assertEquals(null, invalid.outputChemicalItemValue());
    }

    @Test
    void matchesOnlyInputChemicalWithMass() {
        NuclearDecayRule rule = rule(10.0);

        assertTrue(rule.matches(new ChemicalState("chemlib:uranium", 1.0, 1.0, 293.0, 0.0, 0.0)));
        assertFalse(rule.matches(new ChemicalState("chemlib:thorium", 1.0, 1.0, 293.0, 0.0, 0.0)));
        assertFalse(rule.matches(new ChemicalState("chemlib:uranium", 0.0, 1.0, 293.0, 0.0, 0.0)));
    }

    @Test
    void isotopeMassNumberParsesSymbolAndRejectsMalformedValues() {
        assertEquals(238, rule(10.0).isotopeMassNumber());
        NuclearDecayRule malformed = new NuclearDecayRule(
            "test", "chemlib:uranium", "", "", "not-an-isotope", 10.0,
            1.0, 0.0, 0.0, 0.0, 0.0f
        );
        assertEquals(0, malformed.isotopeMassNumber());
    }

    @Test
    void daughterIsotopeUsesExplicitDataAndLegacyMassRatioFallback() {
        NuclearDecayRule explicit = new NuclearDecayRule(
            "test", "chemlib:radon", "chemlib:polonium", "", "Rn-222", "Po-218",
            10.0, 218.0 / 222.0, 0.0, 0.0, 0.0, 0.0f
        );
        NuclearDecayRule legacy = new NuclearDecayRule(
            "test", "chemlib:bismuth", "chemlib:thallium", "", "Bi-209",
            10.0, 205.0 / 209.0, 0.0, 0.0, 0.0, 0.0f
        );

        assertEquals(218, explicit.daughterIsotopeMassNumber());
        assertEquals(205, legacy.daughterIsotopeMassNumber());
    }

    private static NuclearDecayRule rule(double halfLifeSeconds) {
        return new NuclearDecayRule(
            "test",
            "chemlib:uranium",
            "chemlib:thorium",
            "",
            "U-238",
            halfLifeSeconds,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0f
        );
    }
}
