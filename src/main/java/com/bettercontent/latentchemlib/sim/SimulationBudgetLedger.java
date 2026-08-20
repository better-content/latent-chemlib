package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.SchedulerProfile;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class SimulationBudgetLedger<K> {
    private final Map<K, EnumMap<SimulationBudget, Integer>> spentByKey = new HashMap<>();

    public boolean trySpend(K key, SimulationBudget budget, int amount, SchedulerProfile profile) {
        if (amount <= 0) return true;
        int max = limit(budget, profile);
        EnumMap<SimulationBudget, Integer> spent = spentByKey.computeIfAbsent(key, ignored -> new EnumMap<>(SimulationBudget.class));
        int current = spent.getOrDefault(budget, 0);
        if (current + amount > max) return false;
        spent.put(budget, current + amount);
        return true;
    }

    /** Atomically reserves a whole transaction or leaves every budget unchanged. */
    public boolean trySpendAll(K key, Map<SimulationBudget, Integer> amounts, SchedulerProfile profile) {
        EnumMap<SimulationBudget, Integer> spent = spentByKey.computeIfAbsent(key, ignored -> new EnumMap<>(SimulationBudget.class));
        for (var entry : amounts.entrySet()) {
            int amount = Math.max(0, entry.getValue());
            if (spent.getOrDefault(entry.getKey(), 0) + amount > limit(entry.getKey(), profile)) return false;
        }
        for (var entry : amounts.entrySet()) {
            int amount = Math.max(0, entry.getValue());
            if (amount > 0) spent.merge(entry.getKey(), amount, Integer::sum);
        }
        return true;
    }

    public int spent(K key, SimulationBudget budget) {
        EnumMap<SimulationBudget, Integer> spent = spentByKey.get(key);
        return spent == null ? 0 : spent.getOrDefault(budget, 0);
    }

    public void reset(K key) {
        spentByKey.remove(key);
    }

    public void resetAll() {
        spentByKey.clear();
    }

    public static int limit(SimulationBudget budget, SchedulerProfile profile) {
        return switch (budget) {
            case MACHINE_UPDATES -> profile.machineUpdatesPerSecond();
            case NEIGHBOR_OPS -> profile.neighborOpsPerSecond();
            case ESCAPE_SCANS -> profile.escapeScansPerSecond();
            case NUCLEAR_SURFACE_SCANS -> profile.nuclearSurfaceScansPerSecond();
            case NUCLEAR_STACK_EVALUATIONS -> profile.nuclearStackEvaluationsPerSecond();
            case NUCLEAR_STATE_EVALUATIONS -> profile.nuclearStateEvaluationsPerSecond();
            case NUCLEAR_MUTATIONS -> profile.nuclearMutationsPerSecond();
            case NUCLEAR_RADIATION_EMISSIONS -> profile.nuclearRadiationEmissionsPerSecond();
            case NUCLEAR_HEAT_EMISSIONS -> profile.nuclearHeatEmissionsPerSecond();
        };
    }
}
