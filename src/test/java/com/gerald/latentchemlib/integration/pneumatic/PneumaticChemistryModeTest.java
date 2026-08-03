package com.gerald.latentchemlib.integration.pneumatic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PneumaticChemistryModeTest {
    @Test
    void legacyOrUnknownStateDefaultsToNativeAirWithoutMigration() {
        assertEquals(PneumaticChemistryMode.AIR, PneumaticChemistryMode.load(null));
        assertEquals(PneumaticChemistryMode.AIR, PneumaticChemistryMode.load(""));
        assertEquals(PneumaticChemistryMode.AIR, PneumaticChemistryMode.load("OLD_UNKNOWN_MODE"));
    }

    @Test
    void selectionIsExplicitAndReversible() {
        assertEquals(PneumaticChemistryMode.CHEMICAL, PneumaticChemistryMode.AIR.next());
        assertEquals(PneumaticChemistryMode.AIR, PneumaticChemistryMode.CHEMICAL.next());
        assertEquals(PneumaticChemistryMode.CHEMICAL, PneumaticChemistryMode.load("CHEMICAL"));
    }
}
