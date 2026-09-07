package com.bettercontent.latentchemlib.data;

public record SchedulerProfile(
    int escapeScansPerSecond,
    int nuclearSurfaceScansPerSecond,
    int nuclearStackEvaluationsPerSecond,
    int nuclearStateEvaluationsPerSecond,
    int nuclearMutationsPerSecond,
    int nuclearRadiationEmissionsPerSecond,
    int nuclearHeatEmissionsPerSecond
) {
    public static SchedulerProfile defaults() {
        return new SchedulerProfile(64, 512, 512, 128, 64, 64, 64);
    }
}
