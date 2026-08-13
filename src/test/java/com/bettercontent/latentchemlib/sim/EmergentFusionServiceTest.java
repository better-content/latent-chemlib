package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.NuclearPhenomenaProfile;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmergentFusionServiceTest {
    @Test
    void exactlyOneEndpointOwnsAnOpposingCollision() {
        BlockPos west = new BlockPos(2, 4, 6);
        BlockPos east = new BlockPos(4, 4, 6);

        assertTrue(EmergentFusionService.isAuthority(west, east));
        assertFalse(EmergentFusionService.isAuthority(east, west));
    }

    @Test
    void unrelatedCloudsFailTheCheapGateBeforeNeighborScanning() {
        ChemicalState argon = new ChemicalState("chemlib:argon", 1_000.0, 12.0, 9_000.0, 2.0, 100_000.0);

        assertFalse(NuclearPhenomenaMath.isFusionStreamCandidate(argon, NuclearPhenomenaProfile.defaults()));
    }
}
