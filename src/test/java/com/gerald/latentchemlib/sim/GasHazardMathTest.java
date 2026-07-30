package com.gerald.latentchemlib.sim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GasHazardMathTest {
    @Test
    void wholeUnitsIgnoreSubUnitWisps() {
        assertEquals(0, GasHazardMath.wholeUnits(15.99));
        assertEquals(1, GasHazardMath.wholeUnits(16.0));
        assertEquals(2, GasHazardMath.wholeUnits(47.99));
    }

    @Test
    void mixedFlammableGasesShareTheExplosiveThreshold() {
        double fraction = GasHazardMath.explosiveFraction(List.of(
            new GasHazardMath.Contribution(2.0, 4),
            new GasHazardMath.Contribution(4.0, 8),
            new GasHazardMath.Contribution(100.0, 0)
        ));

        assertEquals(1.0, fraction);
    }

    @Test
    void blastPowerUsesConservativeCubeRootCap() {
        assertEquals(2.0f, GasHazardMath.blastPower(1.0));
        assertEquals(4.0f, GasHazardMath.blastPower(64.0));
        assertEquals(8.0f, GasHazardMath.blastPower(4096.0));
    }

    @Test
    void purifierProtectionAttenuatesAndClampsExposure() {
        assertEquals(10, GasHazardMath.attenuateUnits(10, 0.0));
        assertEquals(6, GasHazardMath.attenuateUnits(10, 0.4));
        assertEquals(0, GasHazardMath.attenuateUnits(10, 1.5));
    }
}
