package com.bettercontent.latentchemlib.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadioactiveFormResolverTest {
    @Test
    void materialUnitMassUsesIsotopeMassNumberNotAtomicNumber() {
        assertEquals(209.0, RadioactiveFormResolver.unitMass(209, 1.0));
        assertEquals(1_881.0, RadioactiveFormResolver.unitMass(209, 9.0));
        assertEquals(209.0 / 9.0, RadioactiveFormResolver.unitMass(209, 1.0 / 9.0), 1.0e-12);
    }
}
