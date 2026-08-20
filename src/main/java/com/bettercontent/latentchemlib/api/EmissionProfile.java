package com.bettercontent.latentchemlib.api;

/** Stable, read-only view of one resolved radioactive holder. Strengths are already stack-scaled. */
public record EmissionProfile(
    String family,
    boolean active,
    double radiationStrength,
    double heatStrength
) {
    public EmissionProfile {
        family = family == null ? "" : family;
        radiationStrength = finiteNonNegative(radiationStrength);
        heatStrength = finiteNonNegative(heatStrength);
        if (!active) {
            radiationStrength = 0.0;
            heatStrength = 0.0;
        }
    }

    public static EmissionProfile inert(String family) {
        return new EmissionProfile(family, false, 0.0, 0.0);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
