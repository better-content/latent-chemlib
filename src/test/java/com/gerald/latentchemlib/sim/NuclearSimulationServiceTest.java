package com.gerald.latentchemlib.sim;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NuclearSimulationServiceTest {
    @Test
    void longHalfLifeUraniumHasNoNormalWindowEventWithoutFlux() {
        ChemicalState uranium = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);

        Optional<NuclearSimulationService.NuclearStateEvent> event = NuclearSimulationService.INSTANCE.evaluateState(
            uranium,
            2.0,
            NuclearSimulationService.NuclearEnvironment.EMPTY,
            RandomSource.create(42L)
        );

        assertTrue(event.isEmpty());
    }

    @Test
    void highFluxUraniumCanInduceOneFissionEvent() {
        ChemicalState uranium = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);
        NuclearSimulationService.NuclearEnvironment flux = new NuclearSimulationService.NuclearEnvironment(0.0, 0.0, 40_000.0);

        Optional<NuclearSimulationService.NuclearStateEvent> event = NuclearSimulationService.INSTANCE.evaluateState(
            uranium,
            1.0,
            flux,
            RandomSource.create(42L)
        );

        assertTrue(event.isPresent());
        assertEquals(NuclearSimulationService.NuclearEventType.FISSION, event.get().type());
        assertEquals("chemlib:barium", event.get().outputState().chemicalId());
        assertNull(event.get().outputItem(), "state transformations must not duplicate daughter matter as an item");
    }

    @Test
    void captureFluxUraniumProducesNeptuniumWithoutFissionMassLoss() {
        ChemicalState uranium = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);
        NuclearSimulationService.NuclearEnvironment flux = new NuclearSimulationService.NuclearEnvironment(0.0, 0.0, 6_000.0);

        Optional<NuclearSimulationService.NuclearStateEvent> event = NuclearSimulationService.INSTANCE.evaluateState(
            uranium,
            1.0,
            flux,
            RandomSource.create(7L)
        );

        assertTrue(event.isPresent());
        assertEquals(NuclearSimulationService.NuclearEventType.CAPTURE, event.get().type());
        assertEquals("chemlib:neptunium", event.get().outputState().chemicalId());
        assertEquals(995.0, event.get().outputState().mass());
    }

    @Test
    void emptyStateFluxFallsBackToExternalFluxOnly() {
        NuclearSimulationService.NuclearEnvironment flux = new NuclearSimulationService.NuclearEnvironment(4.0, 0.50, 600.0);

        assertEquals(600.0, NuclearSimulationService.INSTANCE.neutronFlux(ChemicalState.empty(), flux));
    }

    @Test
    void inducedFissionPreservesNonReactingMixtureComponents() {
        ChemicalState mixture = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0)
            .merge(new ChemicalState("chemlib:argon", 125.0, 0.5, 293.0, 0.0, 0.0));
        NuclearSimulationService.NuclearEnvironment flux = new NuclearSimulationService.NuclearEnvironment(0.0, 0.0, 40_000.0);

        var event = NuclearSimulationService.INSTANCE.evaluateState(mixture, 1.0, flux, RandomSource.create(42L));

        assertTrue(event.isPresent());
        assertEquals(520.0, event.get().outputState().massOf("chemlib:barium"));
        assertEquals(125.0, event.get().outputState().massOf("chemlib:argon"));
        assertEquals(645.0, event.get().outputState().mass());
    }

    @Test
    void scannerCursorResumesFromAdvancedPosition() {
        assertEquals(0, NuclearSurfaceScanner.advanceCursor(0, 0, 4));
        assertEquals(3, NuclearSurfaceScanner.advanceCursor(1, 5, 2));
        assertEquals(1, NuclearSurfaceScanner.advanceCursor(4, 5, 2));
    }
}
