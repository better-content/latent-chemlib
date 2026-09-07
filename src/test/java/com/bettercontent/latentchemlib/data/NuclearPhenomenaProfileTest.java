package com.bettercontent.latentchemlib.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NuclearPhenomenaProfileTest {
    @Test
    void defaultsKeepWorldNuclearThresholdsExplicit() {
        NuclearPhenomenaProfile profile = NuclearPhenomenaProfile.defaults();

        assertEquals(256.0, profile.fissionMinimumFuelMass());
        assertEquals(0.65, profile.fissionMinimumFuelFraction());
        assertEquals(227, profile.fissionMinimumIsotopeMassNumber());
        assertEquals(209, profile.decayMinimumIsotopeMassNumber());
        assertEquals(4.0, profile.decayMinimumSpecificHeatPerSecond());
        assertEquals(34.5, profile.fissionMinimumFissilityIndex());
        assertEquals(0.30, profile.fissionMinimumContactFraction());
    }

    @Test
    void validJsonOverridesCalibrationWithoutDefiningMachines() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", NuclearPhenomenaProfile.SCHEMA);
        json.addProperty("fission_minimum_fuel_mass", 512.0);

        NuclearPhenomenaProfile profile = NuclearPhenomenaProfile.fromJson(json);

        assertEquals(512.0, profile.fissionMinimumFuelMass());
        assertEquals(NuclearPhenomenaProfile.defaults().fissionBatchMass(), profile.fissionBatchMass());
    }
}
