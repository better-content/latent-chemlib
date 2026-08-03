package com.gerald.latentchemlib.sim;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void highFluxConfiguredHeavyFuelCanInduceOneFissionEvent() {
        ChemicalState californium = new ChemicalState("chemlib:californium", 1_000.0, 8.0, 900.0, 0.0, 0.0);

        var event = NuclearPhenomenaMath.fission(
            californium, 40_000.0, 0.35, 0.5,
            com.gerald.latentchemlib.data.NuclearPhenomenaProfile.defaults(), id -> id.equals("chemlib:californium")
        ).orElseThrow();

        assertTrue(event.output().massOf("chemlib:barium") > 0.0);
        assertTrue(event.output().massOf("chemlib:krypton") > 0.0);
    }

    @Test
    void unloadedUnitContextDoesNotInferCaptureFromElementNames() {
        ChemicalState uranium = new ChemicalState("chemlib:uranium", 1_000.0, 1.0, 293.0, 0.0, 0.0);
        NuclearSimulationService.NuclearEnvironment flux = new NuclearSimulationService.NuclearEnvironment(0.0, 0.0, 6_000.0);

        Optional<NuclearSimulationService.NuclearStateEvent> event = NuclearSimulationService.INSTANCE.evaluateState(
            uranium,
            1.0,
            flux,
            RandomSource.create(7L)
        );

        assertTrue(event.isEmpty());
    }

    @Test
    void emptyStateFluxFallsBackToExternalFluxOnly() {
        NuclearSimulationService.NuclearEnvironment flux = new NuclearSimulationService.NuclearEnvironment(4.0, 0.50, 600.0);

        assertEquals(600.0, NuclearSimulationService.INSTANCE.neutronFlux(ChemicalState.empty(), flux));
    }

    @Test
    void inducedFissionPreservesNonReactingMixtureComponents() {
        ChemicalState mixture = new ChemicalState("chemlib:uranium", 1_000.0, 8.0, 900.0, 0.0, 0.0)
            .merge(new ChemicalState("chemlib:argon", 125.0, 0.5, 293.0, 0.0, 0.0));
        NuclearSimulationService.NuclearEnvironment flux = new NuclearSimulationService.NuclearEnvironment(0.35, 0.0, 40_000.0, 0.5);

        var event = NuclearPhenomenaMath.fission(
            mixture, 40_000.0, flux.moderation(), flux.contactFraction(),
            com.gerald.latentchemlib.data.NuclearPhenomenaProfile.defaults(), id -> id.equals("chemlib:uranium")
        ).orElseThrow();

        assertTrue(event.output().massOf("chemlib:barium") > 0.0);
        assertTrue(event.output().massOf("chemlib:krypton") > 0.0);
        assertEquals(125.0, event.output().massOf("chemlib:argon"));
        assertEquals(mixture.mass(), event.output().mass() + 0.016, 1.0e-9);
    }

    @Test
    void scannerCursorResumesFromAdvancedPosition() {
        assertEquals(0, NuclearSurfaceScanner.advanceCursor(0, 0, 4));
        assertEquals(3, NuclearSurfaceScanner.advanceCursor(1, 5, 2));
        assertEquals(1, NuclearSurfaceScanner.advanceCursor(4, 5, 2));
    }

    @Test
    void oneOperationBudgetStillRotatesFirstOpportunityAcrossEveryHolderClass() {
        Set<Integer> visited = new LinkedHashSet<>();
        int cursor = 0;
        for (int constrainedRound = 0; constrainedRound < 8; constrainedRound++) {
            int selected = NuclearSurfaceScanner.surfaceClassAt(cursor, 0);
            visited.add(selected);
            cursor = NuclearSurfaceScanner.surfaceClassAfter(selected);
        }

        assertEquals(Set.of(0, 1, 2, 3), visited);
        assertEquals(0, cursor);
    }
}
