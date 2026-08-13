package com.bettercontent.latentchemlib.integration.adpother;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdpotherEmissionContextTest {
    @Test
    void classifiesRepresentativeEmitterHeatSources() {
        assertEquals(1800.0, AdpotherEmissionContext.classify("create:blaze_burner furnace").temperature());
        assertEquals(1400.0, AdpotherEmissionContext.classify("minecraft:blast_furnace").temperature());
        assertEquals(2200.0, AdpotherEmissionContext.classify("minecraft:tnt").temperature());
        assertEquals(3000.0, AdpotherEmissionContext.classify("minecraft:dragon_breath").temperature());
    }

    @Test
    void nestedContextsRestoreTheirParent() {
        AdpotherEmissionContext.push(new AdpotherEmissionContext(700.0, 0.0, 12.0));
        try {
            int result = AdpotherEmissionContext.callWith(
                new AdpotherEmissionContext(1400.0, 0.01, 36.0),
                () -> (int) AdpotherEmissionContext.current().orElseThrow().temperature()
            );
            assertEquals(1400, result);
            assertEquals(700.0, AdpotherEmissionContext.current().orElseThrow().temperature());
        } finally {
            AdpotherEmissionContext.pop();
        }
    }
}
