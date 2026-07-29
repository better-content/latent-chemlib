package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.MachineProfile;
import com.gerald.latentchemlib.data.ReactionRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class ChamberPacingSimulator {
    private static final double BASELINE_TEMPERATURE = 293.0;

    private ChamberPacingSimulator() {}

    public record Summary(int totalRules, int reachableRules, double minimumSeconds, double p90Seconds, double maximumSeconds) {}

    public static double secondsFromBaseline(ReactionRule rule, MachineProfile profile) {
        if (rule == null || profile == null) return Double.POSITIVE_INFINITY;
        if (rule.minMass() > profile.machineMassCapacity()) return Double.POSITIVE_INFINITY;
        if (rule.minCharge() > profile.reactionChamberMaxCharge()) return Double.POSITIVE_INFINITY;
        if (rule.heatCost() > profile.reactionChamberMaxHeat()) return Double.POSITIVE_INFINITY;

        double temperatureSeconds = secondsForDelta(
            Math.max(0.0, rule.minTemperature() - BASELINE_TEMPERATURE),
            profile.chamberTemperaturePerSecond()
        );
        double chargeSeconds = secondsForDelta(
            Math.max(0.0, rule.minCharge()),
            profile.chamberChargePerSecond()
        );
        double energySeconds = secondsForDelta(
            Math.max(0.0, rule.minEnergy()),
            profile.chamberEnergyPerSecond()
        );
        return Math.max(temperatureSeconds, Math.max(chargeSeconds, energySeconds));
    }

    public static Summary summarize(Collection<ReactionRule> rules, MachineProfile profile) {
        if (rules == null || rules.isEmpty()) return new Summary(0, 0, 0.0, 0.0, 0.0);
        ArrayList<Double> reachable = new ArrayList<>();
        for (ReactionRule rule : rules) {
            double seconds = secondsFromBaseline(rule, profile);
            if (Double.isFinite(seconds)) reachable.add(seconds);
        }
        if (reachable.isEmpty()) {
            return new Summary(rules.size(), 0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
        Collections.sort(reachable);
        int p90Index = Math.max(0, (int) Math.ceil(reachable.size() * 0.90) - 1);
        return new Summary(
            rules.size(),
            reachable.size(),
            reachable.get(0),
            reachable.get(p90Index),
            reachable.get(reachable.size() - 1)
        );
    }

    private static double secondsForDelta(double delta, double perSecond) {
        if (delta <= 0.0) return 0.0;
        if (!Double.isFinite(perSecond) || perSecond <= 0.0) return Double.POSITIVE_INFINITY;
        return delta / perSecond;
    }
}
