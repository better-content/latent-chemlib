package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.MachineProfile;
import com.gerald.latentchemlib.data.NumericCurve;
import com.gerald.latentchemlib.data.PresetCurve;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmergentMathTest {
    private final ChemicalTraits traits = new ChemicalTraits(
        1.2, 0.3, 1.0, 0.2, 0.4, 1.5, 0.2, 0.1, 0.0,
        new NumericCurve(PresetCurve.EXPONENTIAL, 1_000.0, 80.0, 1.3, 0.0, 200_000.0)
    );

    @Test
    void escapeEmergesFromPressureAndContainment() {
        ChemicalState volatileState = new ChemicalState("chemlib:hydrogen", 250.0, 2.0, 500.0, 0.0, 80.0);
        assertTrue(EmergentMath.escapePressure(volatileState, traits, 0.0) > 0.0);
        assertTrue(EmergentMath.escapePressure(volatileState, traits, 10_000.0) < 0.0);
    }

    @Test
    void diffusionUsesGradientAndCohesion() {
        ChemicalState source = new ChemicalState("chemlib:hydrogen", 500.0, 4.0, 600.0, 0.0, 120.0);
        assertTrue(EmergentMath.pressureGradient(source, 0.1, traits) > EmergentMath.pressureGradient(source, 3.9, traits));
        assertTrue(EmergentMath.diffusionMass(source, traits) > 0.0);
    }

    @Test
    void diffusionAndPressureClampWhenCohesionDominates() {
        ChemicalTraits cohesive = new ChemicalTraits(
            0.1, 10.0, 1.0, 0.2, 0.1, 0.0, 0.0, 0.0, 0.0,
            NumericCurve.linear(1_000.0, 1.0)
        );
        ChemicalState thin = new ChemicalState("chemlib:argon", 10.0, 0.1, 250.0, 0.0, 0.0);
        assertEquals(0.0, EmergentMath.pressureGradient(thin, 0.0, cohesive));
        assertTrue(EmergentMath.diffusionMass(thin, cohesive) >= 0.0);
    }

    @Test
    void heatFluxAndErosionRespectThresholds() {
        ChemicalState cold = new ChemicalState("chemlib:oxygen", 50.0, 1.0, 290.0, 0.0, 0.0);
        ChemicalState charged = new ChemicalState("chemlib:oxygen", 50.0, 1.0, 900.0, 3.0, 200_000.0);
        assertEquals(0.0, EmergentMath.heatFlux(cold, traits));
        double flux = EmergentMath.heatFlux(charged, traits);
        assertTrue(flux > 0.0);
        assertFalse(EmergentMath.erodes(900.0, 1.0));
        assertTrue(EmergentMath.erodes(flux, 0.1));
    }

    @Test
    void coolingSettlingAndDissipationAreBounded() {
        ChemicalState intense = new ChemicalState("chemlib:helium", 0.5, 0.001, 91.0, 0.001, 1.0);
        ChemicalState settled = EmergentMath.coolAndSettle(intense, traits);
        assertTrue(settled.mass() < intense.mass());
        assertTrue(settled.density() < intense.density());
        assertEquals(90.4, settled.temperature(), 0.0001);
        assertEquals(0.0, settled.charge());
        assertEquals(0.0, settled.energy());
        assertTrue(EmergentMath.shouldDissipate(settled, traits));

        ChemicalState dense = new ChemicalState("chemlib:helium", 10.0, 10.0, 600.0, 0.2, 100.0);
        assertFalse(EmergentMath.shouldDissipate(dense, traits));
    }

    @Test
    void repeatedCoolingEventuallyDissipatesAStableCloud() {
        ChemicalState state = new ChemicalState("chemlib:helium", 200.0, 2.0, 320.0, 0.0, 0.0);

        for (int i = 0; i < 64 && !EmergentMath.shouldDissipate(state, traits); i++) {
            state = EmergentMath.coolAndSettle(state, traits);
        }

        assertTrue(EmergentMath.shouldDissipate(state, traits));
    }

    @Test
    void updateCadenceDistinguishesQuietAndUrgentStates() {
        assertEquals(10, EmergentMath.updateCadence(new ChemicalState("chemlib:neon", 1.0, 1.0, 293.0, 0.0, 0.0)));
        assertEquals(5, EmergentMath.updateCadence(new ChemicalState("chemlib:neon", 1.0, 1.0, 6_000.0, 1.0, 20_000.0)));
    }

    @Test
    void directionalDiffusionFavorsLateralSpreadOverVerticalColumns() {
        ChemicalState warmGas = new ChemicalState("chemlib:hydrogen", 500.0, 4.0, 600.0, 0.0, 120.0);
        double horizontal = EmergentMath.directionalDiffusionWeight(warmGas, Direction.NORTH, 10.0);
        double upward = EmergentMath.directionalDiffusionWeight(warmGas, Direction.UP, 10.0);
        double downward = EmergentMath.directionalDiffusionWeight(warmGas, Direction.DOWN, 10.0);

        assertTrue(horizontal > upward);
        assertTrue(upward > downward);
    }

    @Test
    void fusionInterceptUsesContinuousEnergyTerms() {
        ChemicalState cool = new ChemicalState("chemlib:hydrogen", 500.0, 0.4, 300.0, 0.0, 100.0);
        ChemicalState hot = new ChemicalState("chemlib:hydrogen", 500.0, 8.0, 8_000.0, 2.0, 80_000.0);
        assertFalse(EmergentMath.fusionIntercept(cool, cool, traits, 2, 0.5));
        assertTrue(EmergentMath.fusionIntercept(hot, hot, traits, 2, 2.0));
    }

    @Test
    void neutronFluxIsContinuousAndModerated() {
        ChemicalState state = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);
        double unmoderated = EmergentMath.neutronFlux(state, traits, 0.0);
        double moderated = EmergentMath.neutronFlux(state, traits, 10.0);
        assertTrue(unmoderated > moderated);
        assertTrue(moderated > 0.0);
    }

    @Test
    void chamberAgitationAddsHeatChargeAndEnergyOnlyWhenMatterExists() {
        MachineProfile profile = MachineProfile.defaults();
        ChemicalState empty = ChemicalState.empty();
        assertSame(empty, EmergentMath.chamberAgitation(empty, profile));

        ChemicalState charged = EmergentMath.chamberAgitation(
            new ChemicalState("chemlib:hydrogen", 100.0, 1.0, 300.0, 19.99, 10.0),
            profile
        );
        assertEquals(2_800.0, charged.temperature());
        assertEquals(20.0, charged.charge());
        assertEquals(30_010.0, charged.energy());
    }
}
