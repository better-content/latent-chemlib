package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.MachineProfile;
import com.bettercontent.latentchemlib.data.ReactionRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChamberPacingSimulatorTest {
    @Test
    void authoredThresholdEnvelopeMeetsPacingGates() {
        List<ReactionRule> envelope = new ArrayList<>();
        envelope.add(rule("fastest", 258.0, 2_220.0, 1.16, 16_560.0, 110.0f));
        for (int i = 0; i < 154; i++) {
            envelope.add(rule("middle_" + i, 1_000.0, 500_000.0, 10.0, 5_000_000.0, 10_000.0f));
        }
        envelope.add(rule("p90", 4_000.0, 1_218_908.6, 17.48, 16_485_737.1, 25_000.0f));
        for (int i = 0; i < 17; i++) {
            envelope.add(rule("upper_" + i, 4_666.0, 1_517_205.9, 19.72, 21_871_494.6, 26_018.5f));
        }

        ChamberPacingSimulator.Summary summary = ChamberPacingSimulator.summarize(envelope, MachineProfile.defaults());

        assertEquals(173, summary.totalRules());
        assertEquals(173, summary.reachableRules());
        assertEquals(46.4, summary.minimumSeconds(), 0.0001);
        assertEquals(699.2, summary.p90Seconds(), 0.0001);
        assertEquals(788.8, summary.maximumSeconds(), 0.0001);
        assertTrue(summary.minimumSeconds() >= 45.0);
        assertTrue(summary.p90Seconds() <= 12.0 * 60.0);
        assertTrue(summary.maximumSeconds() <= 15.0 * 60.0);
    }

    @Test
    void capabilityFailuresAreReportedAsUnreachable() {
        MachineProfile profile = MachineProfile.defaults();
        List<ReactionRule> rules = List.of(
            rule("mass", 16_001.0, 293.0, 0.0, 0.0, 1.0f),
            rule("charge", 1.0, 293.0, 20.01, 0.0, 1.0f),
            rule("heat", 1.0, 293.0, 0.0, 0.0, 32_001.0f)
        );

        for (ReactionRule rule : rules) {
            assertEquals(Double.POSITIVE_INFINITY, ChamberPacingSimulator.secondsFromBaseline(rule, profile));
        }
        ChamberPacingSimulator.Summary summary = ChamberPacingSimulator.summarize(rules, profile);
        assertEquals(3, summary.totalRules());
        assertEquals(0, summary.reachableRules());
        assertEquals(Double.POSITIVE_INFINITY, summary.minimumSeconds());
        assertEquals(Double.POSITIVE_INFINITY, summary.p90Seconds());
        assertEquals(Double.POSITIVE_INFINITY, summary.maximumSeconds());
    }

    @Test
    void emptyAndInvalidInputsAreBounded() {
        assertEquals(
            new ChamberPacingSimulator.Summary(0, 0, 0.0, 0.0, 0.0),
            ChamberPacingSimulator.summarize(List.of(), MachineProfile.defaults())
        );
        assertEquals(Double.POSITIVE_INFINITY, ChamberPacingSimulator.secondsFromBaseline(null, MachineProfile.defaults()));
        assertEquals(Double.POSITIVE_INFINITY, ChamberPacingSimulator.secondsFromBaseline(rule("valid", 1.0, 293.0, 0.0, 0.0, 1.0f), null));
    }

    private static ReactionRule rule(String id, double mass, double temperature, double charge, double energy, float heat) {
        return new ReactionRule(
            "latent_chemlib:capture/" + id,
            "chemlib:hydrogen",
            "chemlib:helium",
            "",
            mass,
            temperature,
            charge,
            energy,
            1.0,
            0.0,
            0.0,
            0.0,
            heat,
            0.0f
        );
    }
}
