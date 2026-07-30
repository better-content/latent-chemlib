package com.gerald.latentchemlib.integration.adpother;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DelayedEmissionContextStoreTest {
    @Test
    void accumulatorWeightsAndRetainsFullState() {
        DelayedEmissionContextStore.Accumulator accumulator = new DelayedEmissionContextStore.Accumulator();
        accumulator.add(3, new AdpotherEmissionContext(900.0, 0.0, 20.0));
        accumulator.add(1, new AdpotherEmissionContext(2200.0, 0.10, 120.0));

        AdpotherEmissionContext weighted = accumulator.context().orElseThrow();

        assertEquals(1225.0, weighted.temperature());
        assertEquals(0.025, weighted.charge());
        assertEquals(45.0, weighted.energyPerUnit());

        accumulator.consume(2);
        assertEquals(weighted, accumulator.context().orElseThrow());
    }
}
