package com.bettercontent.latentchemlib.sim;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaAdvectionMathTest {
    @Test
    void lavaFlowAddsBoundedHorizontalDriftWithoutInventingLift() {
        Vec3 advected = LavaAdvectionMath.advect(new Vec3(0.16, -0.02, 0.0), new Vec3(1.0, 0.0, 1.0));

        assertTrue(Math.sqrt(advected.x * advected.x + advected.z * advected.z) <= 0.1800001);
        assertTrue(advected.z > 0.0);
        assertEquals(-0.02, advected.y);
    }
}
