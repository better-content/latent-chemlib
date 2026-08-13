package com.bettercontent.latentchemlib.integration.pneumatic;

public enum PneumaticChemistryMode {
    AIR,
    CHEMICAL;

    public PneumaticChemistryMode next() {
        return this == AIR ? CHEMICAL : AIR;
    }

    public static PneumaticChemistryMode load(String value) {
        if (value == null || value.isBlank()) return AIR;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AIR;
        }
    }
}
