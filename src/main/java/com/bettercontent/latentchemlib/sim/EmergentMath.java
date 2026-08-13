package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.ChemicalTraits;
import com.bettercontent.latentchemlib.data.MachineProfile;
import net.minecraft.core.Direction;

public final class EmergentMath {
    private EmergentMath() {}

    public static double escapePressure(ChemicalState state, ChemicalTraits traits, double containmentStrength) {
        return state.mass() * traits.volatility() * (1.0 + state.temperature() / 1_000.0) - containmentStrength;
    }

    public static double pressureGradient(ChemicalState state, double targetDensity, ChemicalTraits traits) {
        double gradient = state.density() - targetDensity;
        return Math.max(0.0, gradient - traits.cohesion()) * (1.0 + state.temperature() / 2_000.0);
    }

    public static double diffusionMass(ChemicalState state, ChemicalTraits traits) {
        double mobility = traits.volatility() * (1.0 + state.temperature() / 900.0) / Math.max(0.08, traits.cohesion() * 0.7);
        return Math.max(0.0, state.mass() * Math.min(0.60, mobility * 0.12));
    }

    public static double heatFlux(ChemicalState state, ChemicalTraits traits) {
        return Math.max(0.0, (state.temperature() - 330.0) * traits.conductivity() + state.charge() * state.energy() * 0.002);
    }

    public static boolean erodes(double heatFlux, double blockResistance) {
        return heatFlux > 900.0 + blockResistance * 450.0;
    }

    public static ChemicalState coolAndSettle(ChemicalState state, ChemicalTraits traits) {
        double densityDecay = Math.min(0.16, 0.08 + traits.volatility() * 0.05 + traits.conductivity() * 0.03);
        double massDecay = Math.min(0.04, 0.01 + traits.volatility() * 0.015 + Math.max(0.0, state.temperature() - 293.0) / 20_000.0);
        double nextMass = Math.max(0.0, state.mass() * (1.0 - massDecay));
        double nextDensity = Math.max(0.0, state.density() * (1.0 - densityDecay));
        double nextEnergy = Math.max(0.0, state.energy() - 12.0 - traits.conductivity() * 8.0);
        double nextTemperature = Math.max(90.0, state.temperature() - traits.conductivity() * 3.0);
        double nextCharge = Math.max(0.0, state.charge() - 0.01 * (1.0 + traits.cohesion()));
        return state.withMass(nextMass).withPhysicalState(nextDensity, nextTemperature, nextCharge, nextEnergy);
    }

    public static boolean shouldDissipate(ChemicalState state, ChemicalTraits traits) {
        return state.mass() < 1.0 || state.density() < Math.max(0.02, traits.cohesion() * 0.015);
    }

    public static int updateCadence(ChemicalState state) {
        double urgency = state.energy() / 10_000.0 + state.temperature() / 3_000.0 + state.charge();
        return urgency > 2.0 ? 5 : 10;
    }

    public static double directionalDiffusionWeight(ChemicalState state, Direction direction, double pressure) {
        if (pressure <= 0.0) return 0.0;
        double thermalLift = Math.max(0.0, Math.min(0.35, (state.temperature() - 293.0) / 1_800.0));
        double directionBias = switch (direction) {
            case UP -> 0.90 + thermalLift;
            case DOWN -> Math.max(0.20, 0.45 - thermalLift * 0.5);
            default -> 1.35;
        };
        return pressure * directionBias;
    }

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
