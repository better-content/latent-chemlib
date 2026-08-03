package com.gerald.latentchemlib.integration.pneumatic;

import com.gerald.latentchemlib.sim.ChemicalState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Explicit conversion from PNCR's native compressed-air ledger to Latent
 * chemical matter. The two stores remain separate everywhere else.
 */
public final class DryAirSeparation {
    public static final int AIR_PER_BATCH = 1_000;
    public static final float MINIMUM_PRESSURE = 1.0f;
    public static final double OUTPUT_MASS = 16.0;

    // Standard dry-air mass fractions. Water is intentionally absent.
    public static final Map<String, Double> MASS_FRACTIONS = Map.of(
        "chemlib:nitrogen", 0.75518,
        "chemlib:oxygen", 0.23135,
        "chemlib:argon", 0.01288,
        "chemlib:carbon_dioxide", 0.00059
    );

    private DryAirSeparation() {}

    public static Optional<Batch> separate(int nativeAir, float pressure, double availableOutputMass) {
        if (nativeAir < AIR_PER_BATCH || pressure < MINIMUM_PRESSURE || availableOutputMass < OUTPUT_MASS) {
            return Optional.empty();
        }
        return Optional.of(new Batch(AIR_PER_BATCH, dryAirState(OUTPUT_MASS)));
    }

    public static ChemicalState dryAirState(double mass) {
        if (!Double.isFinite(mass) || mass <= 0.0) return ChemicalState.empty();
        Map<String, Double> components = new LinkedHashMap<>();
        MASS_FRACTIONS.forEach((id, fraction) -> components.put(id, mass * fraction));
        return new ChemicalState(components, mass / 28.965, 293.15, 0.0, 0.0);
    }

    public record Batch(int consumedNativeAir, ChemicalState output) {}
}
