package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.ChemicalTraits;
import com.bettercontent.latentchemlib.data.NumericCurve;
import com.bettercontent.latentchemlib.data.PresetCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmergentMathTest {
    private final ChemicalTraits traits = new ChemicalTraits(
        1.2, 0.3, 1.0, 0.2, 0.4, 1.5, 0.2, 0.1, 0.0,
        new NumericCurve(PresetCurve.EXPONENTIAL, 1_000.0, 80.0, 1.3, 0.0, 200_000.0)
    );

    @Test
    void neutronFluxIsContinuousAndModerated() {
        ChemicalState state = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);
        double unmoderated = EmergentMath.neutronFlux(state, traits, 0.0);
        double moderated = EmergentMath.neutronFlux(state, traits, 10.0);
        assertTrue(unmoderated > moderated);
        assertTrue(moderated > 0.0);
    }

}
