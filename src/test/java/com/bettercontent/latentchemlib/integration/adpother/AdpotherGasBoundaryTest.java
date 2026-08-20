package com.bettercontent.latentchemlib.integration.adpother;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
