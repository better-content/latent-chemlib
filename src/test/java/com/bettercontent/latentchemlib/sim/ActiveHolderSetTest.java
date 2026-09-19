package com.bettercontent.latentchemlib.sim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void blockedKeepMovesToTailInsteadOfStarvingLaterHolders() {
        ActiveHolderSet<Integer> index = new ActiveHolderSet<>();
        index.add(1);
        index.add(2);
        index.add(3);
        List<Integer> visited = new ArrayList<>();

        for (int round = 0; round < 3; round++) {
            assertEquals(1, index.visit(1, value -> {
                visited.add(value);
                return ActiveHolderSet.Decision.STOP;
            }));
        }

        assertEquals(List.of(1, 2, 3), visited);
    }

    @Test
    void blockedKeepAlsoRotatesWhenAllowanceIsOne() {
        ActiveHolderSet<Integer> index = new ActiveHolderSet<>();
        index.add(1); index.add(2); index.add(3);
        List<Integer> visited = new ArrayList<>();
        for (int round = 0; round < 6; round++) {
            assertEquals(1, index.visit(1, value -> {
                visited.add(value);
                return ActiveHolderSet.Decision.KEEP;
            }));
        }
        assertEquals(List.of(1, 2, 3, 1, 2, 3), visited);
    }

    @Test
    void exhaustedSetDoesNoWork() {
        ActiveHolderSet<Integer> index = new ActiveHolderSet<>();
        assertEquals(0, index.visit(100, value -> { throw new AssertionError("visited empty set"); }));
    }

    @Test
    void mutationDefersNewWorkUntilTheNextRound() {
        ActiveHolderSet<Integer> index = new ActiveHolderSet<>();
        index.add(1);
        index.add(2);
        List<Integer> firstRound = new ArrayList<>();

        assertEquals(2, index.visit(10, value -> {
            firstRound.add(value);
            index.add(3);
            return ActiveHolderSet.Decision.KEEP;
        }));
        assertEquals(List.of(1, 2), firstRound);
        assertFalse(firstRound.contains(3));
        assertEquals(3, index.size());
    }
}
