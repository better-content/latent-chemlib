package com.bettercontent.latentchemlib.data;

public record ChemicalTraits(
    double volatility,
    double cohesion,
    double heatCapacity,
    double conductivity,
    double ionizationTendency,
    double neutronInstability,
    double neutronAbsorption,
    double scattering,
    double containmentStrength,
    NumericCurve fusionBarrier
) {
    public static ChemicalTraits fallback() {
        return new ChemicalTraits(0.2, 0.7, 1.0, 0.12, 0.1, 0.0, 0.05, 0.05, 0.0, NumericCurve.linear(2_000.0, 120.0));
    }
}
