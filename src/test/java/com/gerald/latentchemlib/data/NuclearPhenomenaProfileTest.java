package com.gerald.latentchemlib.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NuclearPhenomenaProfileTest {
    @Test
    void defaultsKeepEmergentThresholdsIndependentOfMachineProfiles() {
        NuclearPhenomenaProfile profile = NuclearPhenomenaProfile.defaults();

        assertEquals(256.0, profile.fissionMinimumFuelMass());
        assertEquals(0.65, profile.fissionMinimumFuelFraction());
        assertEquals(227, profile.fissionMinimumIsotopeMassNumber());
        assertEquals(209, profile.decayMinimumIsotopeMassNumber());
        assertEquals(4.0, profile.decayMinimumSpecificHeatPerSecond());
        assertEquals(34.5, profile.fissionMinimumFissilityIndex());
        assertEquals(0.30, profile.fissionMinimumContactFraction());
        assertEquals(8_000.0, profile.fusionMinimumTemperature());
        assertEquals(4.0, profile.fusionMinimumDensity());
    }

    @Test
    void validJsonOverridesCalibrationWithoutDefiningMachines() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", NuclearPhenomenaProfile.SCHEMA);
        json.addProperty("fission_minimum_fuel_mass", 512.0);
        json.addProperty("fusion_minimum_temperature", 12_000.0);

        NuclearPhenomenaProfile profile = NuclearPhenomenaProfile.fromJson(json);

        assertEquals(512.0, profile.fissionMinimumFuelMass());
        assertEquals(12_000.0, profile.fusionMinimumTemperature());
        assertEquals(NuclearPhenomenaProfile.defaults().fissionBatchMass(), profile.fissionBatchMass());
    }
}
