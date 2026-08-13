package com.bettercontent.latentchemlib.sim;

import net.minecraft.world.phys.Vec3;

/** Conservative bounded drift added to vanilla item motion while carried by lava. */
public final class LavaAdvectionMath {
    private static final double FLOW_ACCELERATION = 0.06;
    private static final double MAX_HORIZONTAL_SPEED = 0.18;

    private LavaAdvectionMath() {}

    public static Vec3 advect(Vec3 current, Vec3 lavaFlow) {
        double x = current.x + lavaFlow.x * FLOW_ACCELERATION;
        double z = current.z + lavaFlow.z * FLOW_ACCELERATION;
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal > MAX_HORIZONTAL_SPEED) {
            double scale = MAX_HORIZONTAL_SPEED / horizontal;
            x *= scale;
            z *= scale;
        }
        return new Vec3(x, current.y, z);
    }
}
