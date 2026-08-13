package com.bettercontent.latentchemlib.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GasFluidCodecTest {
    @Test
    void fixedConversionRoundTripsWholeMillibuckets() {
        assertEquals(16.0, GasFluidCodec.massForMillibuckets(250));
        assertEquals(250, GasFluidCodec.millibucketsForMass(16.0));
        assertEquals(256.0, GasFluidCodec.massForMillibuckets(4_000));
        assertEquals(4_000, GasFluidCodec.millibucketsForMass(256.0));
    }

    @Test
    void conversionNeverExposesFractionalMillibucketsOrNegativeMatter() {
        assertEquals(0.0, GasFluidCodec.massForMillibuckets(-1));
        assertEquals(0, GasFluidCodec.millibucketsForMass(-1.0));
        assertEquals(15, GasFluidCodec.millibucketsForMass(1.0));
        assertEquals(0, GasFluidCodec.millibucketsForMass(Double.NaN));
    }
}
