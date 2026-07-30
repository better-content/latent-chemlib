package com.gerald.latentchemlib.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CloudInsertionServiceTest {
    @Test
    void scalesFullChemicalStateWithoutNormalizingIntensiveValues() {
        ChemicalState oneUnit = new ChemicalState("chemlib:carbon_dioxide", 16.0, 0.18, 900.0, 0.01, 20.0);

        ChemicalState fourUnits = CloudInsertionService.scale(oneUnit, 4);

        assertEquals(64.0, fourUnits.mass());
        assertEquals(0.72, fourUnits.density());
        assertEquals(900.0, fourUnits.temperature());
        assertEquals(0.01, fourUnits.charge());
        assertEquals(80.0, fourUnits.energy());
    }

    @Test
    void negativeUnitCountsScaleToEmptyMatter() {
        ChemicalState oneUnit = new ChemicalState("chemlib:hydrogen", 16.0, 0.4, 700.0, 0.0, 12.0);

        ChemicalState empty = CloudInsertionService.scale(oneUnit, -2);

        assertEquals(0.0, empty.mass());
        assertEquals(0.0, empty.density());
        assertEquals(0.0, empty.energy());
    }

    @Test
    void plumeCandidatesAreUniqueNearestFirstAndChemicalSpecific() {
        var hydrogen = CloudInsertionService.candidateOffsets("chemlib:hydrogen");
        var methane = CloudInsertionService.candidateOffsets("chemlib:methane");

        assertEquals(343, hydrogen.size());
        assertEquals(343, hydrogen.stream().distinct().count());
        assertEquals(net.minecraft.core.BlockPos.ZERO, hydrogen.get(0));
        assertNotEquals(hydrogen.subList(1, 12), methane.subList(1, 12));
    }
}
