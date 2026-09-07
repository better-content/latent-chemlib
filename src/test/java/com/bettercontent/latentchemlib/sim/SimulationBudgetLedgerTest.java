package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.SchedulerProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationBudgetLedgerTest {
    @Test
    void multiBudgetReservationIsAtomic() {
        SimulationBudgetLedger<String> ledger = new SimulationBudgetLedger<>();
        SchedulerProfile profile = SchedulerProfile.defaults();
        assertTrue(ledger.trySpendAll("level", java.util.Map.of(
            SimulationBudget.NUCLEAR_MUTATIONS, profile.nuclearMutationsPerSecond(),
            SimulationBudget.NUCLEAR_HEAT_EMISSIONS, profile.nuclearHeatEmissionsPerSecond()
        ), profile));
        assertFalse(ledger.trySpendAll("level", java.util.Map.of(
            SimulationBudget.NUCLEAR_MUTATIONS, 1,
            SimulationBudget.NUCLEAR_RADIATION_EMISSIONS, 1
        ), profile));
        assertEquals(0, ledger.spent("level", SimulationBudget.NUCLEAR_RADIATION_EMISSIONS));
    }

    private final SchedulerProfile profile = new SchedulerProfile(4, 5, 6, 7, 8, 9, 10);

    @Test
    void limitsMapEveryBudgetToTheConfiguredProfile() {
        assertEquals(4, SimulationBudgetLedger.limit(SimulationBudget.ESCAPE_SCANS, profile));
        assertEquals(5, SimulationBudgetLedger.limit(SimulationBudget.NUCLEAR_SURFACE_SCANS, profile));
        assertEquals(6, SimulationBudgetLedger.limit(SimulationBudget.NUCLEAR_STACK_EVALUATIONS, profile));
        assertEquals(7, SimulationBudgetLedger.limit(SimulationBudget.NUCLEAR_STATE_EVALUATIONS, profile));
        assertEquals(8, SimulationBudgetLedger.limit(SimulationBudget.NUCLEAR_MUTATIONS, profile));
        assertEquals(9, SimulationBudgetLedger.limit(SimulationBudget.NUCLEAR_RADIATION_EMISSIONS, profile));
        assertEquals(10, SimulationBudgetLedger.limit(SimulationBudget.NUCLEAR_HEAT_EMISSIONS, profile));
    }

    @Test
    void spendingTracksPerKeyAndRejectsOverspend() {
        SimulationBudgetLedger<String> ledger = new SimulationBudgetLedger<>();
        assertTrue(ledger.trySpend("overworld", SimulationBudget.ESCAPE_SCANS, 4, profile));
        assertFalse(ledger.trySpend("overworld", SimulationBudget.ESCAPE_SCANS, 1, profile));
        assertEquals(4, ledger.spent("overworld", SimulationBudget.ESCAPE_SCANS));
    }

    @Test
    void zeroAndNegativeSpendAreNoOps() {
        SimulationBudgetLedger<String> ledger = new SimulationBudgetLedger<>();
        assertTrue(ledger.trySpend("overworld", SimulationBudget.ESCAPE_SCANS, 0, profile));
        assertTrue(ledger.trySpend("overworld", SimulationBudget.ESCAPE_SCANS, -10, profile));
        assertEquals(0, ledger.spent("overworld", SimulationBudget.ESCAPE_SCANS));
    }

    @Test
    void resetClearsOneKeyAndResetAllClearsEverything() {
        SimulationBudgetLedger<String> ledger = new SimulationBudgetLedger<>();
        assertTrue(ledger.trySpend("overworld", SimulationBudget.NUCLEAR_SURFACE_SCANS, 5, profile));
        assertTrue(ledger.trySpend("nether", SimulationBudget.NUCLEAR_SURFACE_SCANS, 5, profile));

        ledger.reset("overworld");
        assertEquals(0, ledger.spent("overworld", SimulationBudget.NUCLEAR_SURFACE_SCANS));
        assertEquals(5, ledger.spent("nether", SimulationBudget.NUCLEAR_SURFACE_SCANS));

        ledger.resetAll();
        assertEquals(0, ledger.spent("nether", SimulationBudget.NUCLEAR_SURFACE_SCANS));
    }

    @Test
    void defaultSchedulerProfileKeepsExpectedConservativeBudgets() {
        SchedulerProfile defaults = SchedulerProfile.defaults();
        assertEquals(64, defaults.escapeScansPerSecond());
        assertEquals(512, defaults.nuclearSurfaceScansPerSecond());
        assertEquals(512, defaults.nuclearStackEvaluationsPerSecond());
        assertEquals(128, defaults.nuclearStateEvaluationsPerSecond());
        assertEquals(64, defaults.nuclearMutationsPerSecond());
        assertEquals(64, defaults.nuclearRadiationEmissionsPerSecond());
        assertEquals(64, defaults.nuclearHeatEmissionsPerSecond());
    }
}
