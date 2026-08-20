package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.ChemicalTraits;
import com.bettercontent.latentchemlib.data.MachineProfile;
public final class EmergentMath {
    private EmergentMath() {}

    public static boolean fusionIntercept(ChemicalState first, ChemicalState second, ChemicalTraits traits, int productAtomicNumber, double confinement) {
        double collisionEnergy = (first.energy() + second.energy()) * (1.0 + first.charge() + second.charge()) * Math.max(0.1, confinement);
        double densityTerm = Math.sqrt(Math.max(0.0, first.density() * second.density()));
        double barrier = traits.fusionBarrier().sample(productAtomicNumber) / Math.max(0.1, densityTerm);
        return collisionEnergy > barrier;
    }

    public static double neutronFlux(ChemicalState state, ChemicalTraits traits, double moderation) {
        double instability = traits.neutronInstability() * Math.max(0.0, state.mass());
        double damping = Math.max(0.05, 1.0 + moderation * traits.scattering());
        return instability * instability / damping;
    }

    public static ChemicalState chamberAgitation(ChemicalState state, MachineProfile profile) {
        if (state.mass() <= 0.0) return state;
        return new ChemicalState(
            state.components(),
            state.density(),
            state.temperature() + profile.chamberTemperaturePerSecond(),
            Math.min(profile.reactionChamberMaxCharge(), state.charge() + profile.chamberChargePerSecond()),
            state.energy() + profile.chamberEnergyPerSecond()
        );
    }
}
