package com.gerald.latentchemlib.sim;

import com.gerald.latentchemlib.data.ReactionRule;

import java.util.List;
import java.util.Optional;

public final class ReactionRuleSelector {
    private ReactionRuleSelector() {}

    public static Optional<ReactionRule> firstMatch(List<ReactionRule> rules, ChemicalState state, float availableHeat) {
        if (rules == null || state == null || state.mass() <= 0.0) return Optional.empty();
        for (ReactionRule rule : rules) {
            if (rule == null || rule.id().contains(":decay/")) continue;
            if (rule.matches(state, availableHeat)) return Optional.of(rule);
        }
        return Optional.empty();
    }
}
