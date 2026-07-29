package com.gerald.latentchemlib.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record MachineProfile(
    float defaultMaxHeat,
    float reactionChamberMaxHeat,
    double reactionChamberMaxCharge,
    double machineMassCapacity,
    double chamberTemperaturePerSecond,
    double chamberChargePerSecond,
    double chamberEnergyPerSecond
) {
    public static final String SCHEMA = "bc.latent_chemlib.machine_profile.v1";
    private static final MachineProfile DEFAULTS = new MachineProfile(
        4_000.0f,
        32_000.0f,
        20.0,
        16_000.0,
        2_500.0,
        0.025,
        30_000.0
    );

    public static MachineProfile defaults() {
        return DEFAULTS;
    }

    public static MachineProfile fromJson(JsonObject json) {
        MachineProfile fallback = defaults();
        if (!hasSupportedSchema(json)) return fallback;
        return new MachineProfile(
            positiveFloat(json, "default_max_heat", fallback.defaultMaxHeat()),
            positiveFloat(json, "reaction_chamber_max_heat", fallback.reactionChamberMaxHeat()),
            positiveDouble(json, "reaction_chamber_max_charge", fallback.reactionChamberMaxCharge()),
            positiveDouble(json, "machine_mass_capacity", fallback.machineMassCapacity()),
            positiveDouble(json, "chamber_temperature_per_second", fallback.chamberTemperaturePerSecond()),
            positiveDouble(json, "chamber_charge_per_second", fallback.chamberChargePerSecond()),
            positiveDouble(json, "chamber_energy_per_second", fallback.chamberEnergyPerSecond())
        );
    }

    public static boolean hasSupportedSchema(JsonObject json) {
        try {
            JsonElement element = json == null ? null : json.get("schema");
            return element != null && element.isJsonPrimitive() && SCHEMA.equals(element.getAsString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static float positiveFloat(JsonObject json, String key, float fallback) {
        double value = number(json, key, fallback);
        return value > 0.0 && value <= Float.MAX_VALUE ? (float) value : fallback;
    }

    private static double positiveDouble(JsonObject json, String key, double fallback) {
        double value = number(json, key, fallback);
        return value > 0.0 ? value : fallback;
    }

    private static double number(JsonObject json, String key, double fallback) {
        try {
            JsonElement element = json.get(key);
            if (element == null || !element.isJsonPrimitive()) return fallback;
            double value = element.getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
