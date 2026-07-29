package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.ReactionRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionRuleSelectorTest {
    private final ReactionRule fusion = rule("fusion", 6_900.0, 2.04, 53_100.0, 278.0f);
    private final ReactionRule capture = rule("capture", 2_220.0, 1.16, 16_560.0, 110.0f);
    private final List<ReactionRule> orderedRules = List.of(fusion, capture);

    @Test
    void lowerThresholdCaptureWinsDuringOrdinaryConditioning() {
        ChemicalState state = new ChemicalState("chemlib:hydrogen", 500.0, 1.0, 3_000.0, 1.5, 20_000.0);

        assertEquals(capture, ReactionRuleSelector.firstMatch(orderedRules, state, 110.0f).orElseThrow());
    }

    @Test
    void withholdingHeatAllowsConditioningBeforeOrderedFusionSelection() {
        ChemicalState fusionReady = new ChemicalState("chemlib:hydrogen", 500.0, 1.0, 7_000.0, 2.1, 54_000.0);

        assertTrue(ReactionRuleSelector.firstMatch(orderedRules, fusionReady, 0.0f).isEmpty());
        assertEquals(fusion, ReactionRuleSelector.firstMatch(orderedRules, fusionReady, 278.0f).orElseThrow());
    }

    @Test
    void decayRulesAndEmptyStatesAreNotChamberReactions() {
        ReactionRule decay = new ReactionRule(
            "latent_chemlib:decay/test",
            "chemlib:hydrogen",
            "chemlib:helium",
            "",
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0f,
            0.0f
        );

        assertTrue(ReactionRuleSelector.firstMatch(List.of(decay), ChemicalState.empty(), 1_000.0f).isEmpty());
        assertTrue(ReactionRuleSelector.firstMatch(null, ChemicalState.empty(), 1_000.0f).isEmpty());
    }

    private static ReactionRule rule(String family, double temperature, double charge, double energy, float heat) {
        return new ReactionRule(
            "latent_chemlib:" + family + "/hydrogen_to_helium",
            "chemlib:hydrogen",
            "chemlib:helium",
            "",
            family.equals("fusion") ? 492.0 : 258.0,
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
