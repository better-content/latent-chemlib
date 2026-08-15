package com.bettercontent.latentchemlib.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CloudInsertionServiceTest {
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
