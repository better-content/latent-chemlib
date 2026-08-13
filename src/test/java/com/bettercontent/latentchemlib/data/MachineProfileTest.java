package com.bettercontent.latentchemlib.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineProfileTest {
    @Test
    void defaultsExposePackSafeCapabilitiesAndPacing() {
        MachineProfile profile = MachineProfile.defaults();

        assertEquals(4_000.0f, profile.defaultMaxHeat());
        assertEquals(32_000.0f, profile.reactionChamberMaxHeat());
        assertEquals(20.0, profile.reactionChamberMaxCharge());
        assertEquals(16_000.0, profile.machineMassCapacity());
        assertEquals(2_500.0, profile.chamberTemperaturePerSecond());
        assertEquals(0.025, profile.chamberChargePerSecond());
        assertEquals(30_000.0, profile.chamberEnergyPerSecond());
    }

    @Test
    void jsonOverridesEveryMachineLever() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", MachineProfile.SCHEMA);
        json.addProperty("default_max_heat", 5_000.0);
        json.addProperty("reaction_chamber_max_heat", 40_000.0);
        json.addProperty("reaction_chamber_max_charge", 24.0);
        json.addProperty("machine_mass_capacity", 20_000.0);
        json.addProperty("chamber_temperature_per_second", 3_000.0);
        json.addProperty("chamber_charge_per_second", 0.05);
        json.addProperty("chamber_energy_per_second", 35_000.0);

        MachineProfile profile = MachineProfile.fromJson(json);

        assertEquals(5_000.0f, profile.defaultMaxHeat());
        assertEquals(40_000.0f, profile.reactionChamberMaxHeat());
        assertEquals(24.0, profile.reactionChamberMaxCharge());
        assertEquals(20_000.0, profile.machineMassCapacity());
        assertEquals(3_000.0, profile.chamberTemperaturePerSecond());
        assertEquals(0.05, profile.chamberChargePerSecond());
        assertEquals(35_000.0, profile.chamberEnergyPerSecond());
    }

    @Test
    void absentInvalidAndNonPositiveValuesUseDefaults() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", MachineProfile.SCHEMA);
        json.addProperty("default_max_heat", -1.0);
        json.addProperty("reaction_chamber_max_heat", Double.NaN);
        json.addProperty("reaction_chamber_max_charge", "not-a-number");
        json.addProperty("machine_mass_capacity", 0.0);
        json.addProperty("chamber_temperature_per_second", Double.POSITIVE_INFINITY);
        json.addProperty("chamber_charge_per_second", -0.5);

        MachineProfile profile = MachineProfile.fromJson(json);

        assertEquals(MachineProfile.defaults(), profile);
        assertEquals(MachineProfile.defaults(), MachineProfile.fromJson(null));
    }

    @Test
    void oversizedFloatCapacityUsesDefault() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", MachineProfile.SCHEMA);
        json.addProperty("reaction_chamber_max_heat", Double.MAX_VALUE);

        assertEquals(MachineProfile.defaults().reactionChamberMaxHeat(), MachineProfile.fromJson(json).reactionChamberMaxHeat());
    }

    @Test
    void unsupportedOrMissingSchemaIsIgnored() {
        JsonObject unsupported = new JsonObject();
        unsupported.addProperty("schema", "bc.latent_chemlib.machine_profile.v2");
        unsupported.addProperty("reaction_chamber_max_heat", 99_000.0);

        assertEquals(MachineProfile.defaults(), MachineProfile.fromJson(unsupported));
        assertEquals(MachineProfile.defaults(), MachineProfile.fromJson(new JsonObject()));
        assertEquals(MachineProfile.defaults(), MachineProfile.fromJson(null));
    }
}
