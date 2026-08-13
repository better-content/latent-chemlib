package com.bettercontent.latentchemlib.sim;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NeutronEconomyHandlerTest {
    @Test
    void decayedCountBoundsProbability() {
        RandomSource random = RandomSource.create(42L);

        assertEquals(0, NeutronEconomyHandler.decayedCount(64, 0.0, random));
        assertEquals(64, NeutronEconomyHandler.decayedCount(64, 1.0, random));
        assertEquals(0, NeutronEconomyHandler.decayedCount(0, 1.0, random));
    }
}
