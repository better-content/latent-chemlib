package com.gerald.latentchemlib.integration.pneumatic;

import com.gerald.latentchemlib.sim.ChemicalState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DryAirSeparationTest {
    @Test
    void emitsCanonicalDryAirAsAConservedMixture() {
        DryAirSeparation.Batch batch = DryAirSeparation.separate(1_000, 1.0f, 16.0).orElseThrow();
        ChemicalState output = batch.output();

        assertEquals(1_000, batch.consumedNativeAir());
        assertEquals(16.0, output.mass(), 1.0e-9);
        assertEquals(16.0 * 0.75518, output.massOf("chemlib:nitrogen"), 1.0e-9);
        assertEquals(16.0 * 0.23135, output.massOf("chemlib:oxygen"), 1.0e-9);
        assertEquals(16.0 * 0.01288, output.massOf("chemlib:argon"), 1.0e-9);
        assertEquals(16.0 * 0.00059, output.massOf("chemlib:carbon_dioxide"), 1.0e-9);
        assertFalse(output.isPure());
    }

    @Test
    void refusesAmbientShortageLowPressureAndFullOutput() {
        assertFalse(DryAirSeparation.separate(999, 2.0f, 16.0).isPresent());
        assertFalse(DryAirSeparation.separate(1_000, 0.999f, 16.0).isPresent());
        assertFalse(DryAirSeparation.separate(1_000, 2.0f, 15.999).isPresent());
    }

    @Test
    void invalidDryAirMassProducesNoChemicalMatter() {
        assertEquals(0.0, DryAirSeparation.dryAirState(-1.0).mass());
        assertEquals(0.0, DryAirSeparation.dryAirState(Double.NaN).mass());
    }
}
