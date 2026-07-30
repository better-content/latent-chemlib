package com.gerald.latentchemlib.sim;

import java.util.Collection;

public final class GasHazardMath {
    private GasHazardMath() {}

    public static int wholeUnits(double mass) {
        return (int) Math.floor(Math.max(0.0, mass) / CloudInsertionService.MASS_PER_ADPOTHER_UNIT);
    }

    public static double explosiveFraction(Collection<Contribution> contributions) {
        return contributions.stream()
            .filter(contribution -> contribution.lowerExplosiveLimit() > 0)
            .mapToDouble(contribution ->
                Math.max(0.0, contribution.units()) / contribution.lowerExplosiveLimit()
            )
            .sum();
    }

    public static float blastPower(double units) {
        return (float) Math.max(2.0, Math.min(8.0, Math.cbrt(Math.max(0.0, units))));
    }

    public static int attenuateUnits(int units, double protectedFraction) {
        int boundedUnits = Math.max(0, units);
        double boundedProtection = Math.max(0.0, Math.min(1.0, protectedFraction));
        return Math.max(0, boundedUnits - (int) Math.round(boundedUnits * boundedProtection));
    }

    public record Contribution(double units, int lowerExplosiveLimit) {}
}
