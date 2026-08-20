package com.bettercontent.latentchemlib.sim;

/** Latent-local heat buffer contract; external heat mods consume emission profiles instead. */
public interface HeatReceiver {
    float addHeat(float amount, boolean simulate);
}
