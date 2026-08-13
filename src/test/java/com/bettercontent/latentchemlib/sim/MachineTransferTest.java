package com.bettercontent.latentchemlib.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineTransferTest {
    @Test
    void captureIsLimitedByCloudShareTransferRateAndRemainingCapacity() {
        assertEquals(25.0, MachineTransfer.captureAmount(0.0, 100.0, 16_000.0));
        assertEquals(250.0, MachineTransfer.captureAmount(0.0, 2_000.0, 16_000.0));
        assertEquals(100.0, MachineTransfer.captureAmount(15_900.0, 2_000.0, 16_000.0));
    }

    @Test
    void fullOrInvalidSourcesCannotOvershootCapacity() {
        assertEquals(0.0, MachineTransfer.captureAmount(16_000.0, 2_000.0, 16_000.0));
        assertEquals(0.0, MachineTransfer.captureAmount(16_250.0, 2_000.0, 16_000.0));
        assertEquals(0.0, MachineTransfer.captureAmount(0.0, -1.0, 16_000.0));
    }
}
