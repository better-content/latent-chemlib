package com.bettercontent.latentchemlib.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChemicalTraitsTest {
    @Test
    void fallbackTraitsAreStableAndNonRadioactive() {
        ChemicalTraits fallback = ChemicalTraits.fallback();
        assertEquals(0.2, fallback.volatility());
        assertEquals(0.7, fallback.cohesion());
        assertEquals(1.0, fallback.heatCapacity());
        assertEquals(0.12, fallback.conductivity());
        assertEquals(0.1, fallback.ionizationTendency());
        assertEquals(0.0, fallback.neutronInstability());
        assertEquals(0.05, fallback.neutronAbsorption());
        assertEquals(0.05, fallback.scattering());
        assertEquals(0.0, fallback.containmentStrength());
        assertEquals(2_120.0, fallback.fusionBarrier().sample(1.0));
    }
}
