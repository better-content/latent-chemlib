package com.bettercontent.latentchemlib.sim;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphericPollutantStateTest {
    @Test
    void persistsOnlyPollutantIdentityAndWholeUnits() {
        AtmosphericPollutantState state = new AtmosphericPollutantState("hydrogen", 7);
        assertEquals(state, AtmosphericPollutantState.load(state.save()));
        assertEquals(3, state.save().getAllKeys().size());
    }

    @Test
    void rejectsLegacyAndNormalizesEmptyState() {
        assertEquals(AtmosphericPollutantState.EMPTY, AtmosphericPollutantState.load(new CompoundTag()));
        assertEquals(AtmosphericPollutantState.EMPTY, new AtmosphericPollutantState("hydrogen", -1));
        assertEquals(AtmosphericPollutantState.EMPTY, new AtmosphericPollutantState(null, 4));
    }
}
