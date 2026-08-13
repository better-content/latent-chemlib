package com.bettercontent.latentchemlib.sim;

public final class ChemicalCloudVisuals {
    private ChemicalCloudVisuals() {}

    public static int diffusionTier(ChemicalState state) {
        if (state.mass() <= 0.0 || state.density() < 0.25) return 3;
        if (state.density() < 1.0) return 2;
        if (state.density() < 3.0) return 1;
        return 0;
    }
}
