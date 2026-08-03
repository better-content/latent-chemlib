package com.gerald.latentchemlib.sim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveHolderSetTest {
    @Test
    void visitCostIsBoundedByBudgetAtLargeScaleAndRotatesFairly() {
        ActiveHolderSet<Integer> index = new ActiveHolderSet<>();
        for (int value = 0; value < 100_000; value++) index.add(value);
        List<Integer> first = new ArrayList<>();
        List<Integer> second = new ArrayList<>();

        assertEquals(64, index.visit(64, value -> {
            first.add(value);
            return ActiveHolderSet.Decision.KEEP;
        }));
        assertEquals(64, index.visit(64, value -> {
            second.add(value);
            return ActiveHolderSet.Decision.KEEP;
        }));

        assertEquals(100_000, index.size());
        assertEquals(0, first.get(0));
        assertEquals(64, second.get(0));
    }

    @Test
    void removeAndStopHaveExplicitRetentionSemantics() {
        ActiveHolderSet<Integer> index = new ActiveHolderSet<>();
        index.add(1);
        index.add(2);
        index.add(3);

        assertEquals(2, index.visit(10, value -> value == 1
            ? ActiveHolderSet.Decision.REMOVE
            : ActiveHolderSet.Decision.STOP));
        assertEquals(2, index.size());
        index.removeIf(value -> value == 3);
        assertEquals(1, index.size());
        index.remove(2);
        assertEquals(0, index.size());
    }
}
