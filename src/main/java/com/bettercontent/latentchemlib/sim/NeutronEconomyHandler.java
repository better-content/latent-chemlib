package com.bettercontent.latentchemlib.sim;

import net.minecraft.util.RandomSource;

final class NeutronEconomyHandler {
    private NeutronEconomyHandler() {
    }

    static int decayedCount(int count, double probability, RandomSource random) {
        if (count <= 0 || probability <= 0.0) return 0;
        if (probability >= 1.0) return count;
        int decayed = 0;
        for (int i = 0; i < count; i++) {
            if (random.nextDouble() < probability) decayed++;
        }
        return decayed;
    }
}
