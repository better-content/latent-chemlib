package com.gerald.latentchemlib.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Data-calibrated thresholds for emergent nuclear behavior; it defines no machine. */
public record NuclearPhenomenaProfile(
    double fissionMinimumFuelMass,
    double fissionMinimumFuelFraction,
    int fissionMinimumIsotopeMassNumber,
    double fissionMinimumFissilityIndex,
    double fissionMinimumDensity,
    double fissionMinimumTemperature,
    double fissionMinimumNeutronFlux,
    double fissionMinimumModeration,
    double fissionMinimumContactFraction,
    double fissionBatchMass,
    double fissionPrimaryDaughterFraction,
    double fissionMassDefectFraction,
    String fissionPrimaryDaughter,
    String fissionSecondaryDaughter,
    float fissionHeatEmission,
    int fissionRadiationLevel,
    double fusionMinimumTemperature,
    double fusionMinimumDensity,
    double fusionMinimumEnergy,
    double fusionBatchMassPerStream,
    double fusionMassDefectFraction,
    float fusionHeatEmission,
    int fusionRadiationLevel,
    double decayReferenceMass,
    double decayHalfLifeLogScale,
    float surroundingMeltHeatThreshold
) {
    public static final String SCHEMA = "bc.latent_chemlib.nuclear_phenomena.v1";
    private static final NuclearPhenomenaProfile DEFAULTS = new NuclearPhenomenaProfile(
        256.0, 0.65, 227, 34.5, 4.0, 600.0, 18_000.0, 0.20, 0.30,
        8.0, 0.58, 0.002, "chemlib:barium", "chemlib:krypton", 12_000.0f, 12,
        8_000.0, 4.0, 50_000.0, 4.0, 0.007, 24_000.0f, 8,
        256.0, 1.0,
        10_000.0f
    );

    public static NuclearPhenomenaProfile defaults() {
        return DEFAULTS;
    }

    public static boolean hasSupportedSchema(JsonObject json) {
        try {
            JsonElement element = json == null ? null : json.get("schema");
            return element != null && SCHEMA.equals(element.getAsString());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static NuclearPhenomenaProfile fromJson(JsonObject json) {
        NuclearPhenomenaProfile fallback = defaults();
        if (!hasSupportedSchema(json)) return fallback;
        return new NuclearPhenomenaProfile(
            positive(json, "fission_minimum_fuel_mass", fallback.fissionMinimumFuelMass()),
            fraction(json, "fission_minimum_fuel_fraction", fallback.fissionMinimumFuelFraction()),
            positiveInt(json, "fission_minimum_isotope_mass_number", fallback.fissionMinimumIsotopeMassNumber()),
            positive(json, "fission_minimum_fissility_index", fallback.fissionMinimumFissilityIndex()),
            positive(json, "fission_minimum_density", fallback.fissionMinimumDensity()),
            positive(json, "fission_minimum_temperature", fallback.fissionMinimumTemperature()),
            positive(json, "fission_minimum_neutron_flux", fallback.fissionMinimumNeutronFlux()),
            positive(json, "fission_minimum_moderation", fallback.fissionMinimumModeration()),
            fraction(json, "fission_minimum_contact_fraction", fallback.fissionMinimumContactFraction()),
            positive(json, "fission_batch_mass", fallback.fissionBatchMass()),
            fraction(json, "fission_primary_daughter_fraction", fallback.fissionPrimaryDaughterFraction()),
            fraction(json, "fission_mass_defect_fraction", fallback.fissionMassDefectFraction()),
            string(json, "fission_primary_daughter", fallback.fissionPrimaryDaughter()),
            string(json, "fission_secondary_daughter", fallback.fissionSecondaryDaughter()),
            positiveFloat(json, "fission_heat_emission", fallback.fissionHeatEmission()),
            positiveInt(json, "fission_radiation_level", fallback.fissionRadiationLevel()),
            positive(json, "fusion_minimum_temperature", fallback.fusionMinimumTemperature()),
            positive(json, "fusion_minimum_density", fallback.fusionMinimumDensity()),
            positive(json, "fusion_minimum_energy", fallback.fusionMinimumEnergy()),
            positive(json, "fusion_batch_mass_per_stream", fallback.fusionBatchMassPerStream()),
            fraction(json, "fusion_mass_defect_fraction", fallback.fusionMassDefectFraction()),
            positiveFloat(json, "fusion_heat_emission", fallback.fusionHeatEmission()),
            positiveInt(json, "fusion_radiation_level", fallback.fusionRadiationLevel()),
            positive(json, "decay_reference_mass", fallback.decayReferenceMass()),
            positive(json, "decay_half_life_log_scale", fallback.decayHalfLifeLogScale()),
            positiveFloat(json, "surrounding_melt_heat_threshold", fallback.surroundingMeltHeatThreshold())
        );
    }

    private static String string(JsonObject json, String key, String fallback) {
        try {
            JsonElement element = json == null ? null : json.get(key);
            String value = element == null ? fallback : element.getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static double positive(JsonObject json, String key, double fallback) {
        try {
            JsonElement element = json == null ? null : json.get(key);
            double value = element == null ? fallback : element.getAsDouble();
            return Double.isFinite(value) && value > 0.0 ? value : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static double fraction(JsonObject json, String key, double fallback) {
        double value = positive(json, key, fallback);
        return value > 0.0 && value < 1.0 ? value : fallback;
    }

    private static float positiveFloat(JsonObject json, String key, float fallback) {
        double value = positive(json, key, fallback);
        return value <= Float.MAX_VALUE ? (float) value : fallback;
    }

    private static int positiveInt(JsonObject json, String key, int fallback) {
        try {
            JsonElement element = json == null ? null : json.get(key);
            int value = element == null ? fallback : element.getAsInt();
            return value > 0 ? value : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
