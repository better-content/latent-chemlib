package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.ChemicalTraits;
public final class EmergentMath {
    private EmergentMath() {}

    public static double neutronFlux(ChemicalState state, ChemicalTraits traits, double moderation) {
        double instability = traits.neutronInstability() * Math.max(0.0, state.mass());
        double damping = Math.max(0.05, 1.0 + moderation * traits.scattering());
        return instability * instability / damping;
    }

}
