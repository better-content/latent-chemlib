package com.bettercontent.latentchemlib.integration.adpother;

import net.minecraft.core.BlockPos;
import com.bettercontent.latentchemlib.sim.ChemicalState;
import com.bettercontent.latentchemlib.sim.GasFluidCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AdpotherGasBoundaryTest {
    @Test
    void candidateOrderIsDeterministicAndSpeciesSpecific() {
        var hydrogen = AdpotherGasBoundary.candidateOffsets("chemlib:hydrogen");
        var methane = AdpotherGasBoundary.candidateOffsets("chemlib:methane");

        assertEquals(343, hydrogen.size());
        assertEquals(BlockPos.ZERO, hydrogen.get(0));
        assertEquals(hydrogen, AdpotherGasBoundary.candidateOffsets("chemlib:hydrogen"));
        assertNotEquals(hydrogen, methane);
    }

    @Test
    void rejectsFractionalFluidPayloadBeforeAnySourceCanBeDebited() {
        // 375 mB maps to 24 mass. AdPother stores whole 16-mass units, so the
        // entire source remains owned by its caller instead of losing 125 mB.
        assertEquals(24.0, GasFluidCodec.massForMillibuckets(375));
        ChemicalState partial = new ChemicalState("chemlib:carbon_dioxide", 24.0, 1.0, 293.0, 0.0, 0.0);
        ChemicalState exact = new ChemicalState("chemlib:carbon_dioxide", 32.0, 1.0, 293.0, 0.0, 0.0);

        assertFalse(AdpotherGasBoundary.isExactlyRepresentable(partial));
        assertTrue(AdpotherGasBoundary.isExactlyRepresentable(exact));
    }
}
