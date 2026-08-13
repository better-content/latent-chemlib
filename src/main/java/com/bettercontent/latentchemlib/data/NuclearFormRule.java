package com.bettercontent.latentchemlib.data;

/** Data-driven mapping from a registered material form to one canonical element unit. */
public record NuclearFormRule(String suffix, double materialUnits) {
    public NuclearFormRule {
        suffix = suffix == null ? "" : suffix;
        materialUnits = Double.isFinite(materialUnits) && materialUnits > 0.0 ? materialUnits : 1.0;
    }
}
