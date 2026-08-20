package com.bettercontent.latentchemlib.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmissionProfileTest {
    @Test
    void inertProfilesCannotLeakConfiguredStrength() {
        EmissionProfile profile = new EmissionProfile("uranium", false, 12.0, 3.0);
        assertFalse(profile.active());
        assertEquals(0.0, profile.radiationStrength());
        assertEquals(0.0, profile.heatStrength());
    }

    @Test
    void activeProfileRetainsIndependentStrengths() {
        EmissionProfile profile = new EmissionProfile("thorium", true, 5.0, 1.25);
        assertEquals("thorium", profile.family());
        assertEquals(5.0, profile.radiationStrength());
        assertEquals(1.25, profile.heatStrength());
        assertEquals(64, LatentEmissionProfiles.MAX_STACK_SCALE);
        assertEquals(64, LatentEmissionProfiles.stackScale(4_096));
        assertEquals(12, LatentEmissionProfiles.stackScale(12));
    }
}
