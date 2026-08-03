package com.gerald.latentchemlib.sim;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LoadedExposureClockTest {
    @Test
    void advancesOnlyByExplicitLoadedTicksAndKeepsItsSeed() {
        CompoundTag stackTag = new CompoundTag();

        LoadedExposureClock.Window first = LoadedExposureClock.advance(stackTag, 20L, 42L);
        LoadedExposureClock.Window second = LoadedExposureClock.advance(stackTag, 40L, 999L);

        assertEquals(0L, first.startTick());
        assertEquals(20L, first.endTick());
        assertEquals(20L, second.startTick());
        assertEquals(60L, second.endTick());
        assertEquals(first.seed(), second.seed());
    }

    @Test
    void deterministicRollIsStableForOneWindowAndChannel() {
        LoadedExposureClock.Window window = new LoadedExposureClock.Window(20L, 40L, 123L);

        double first = LoadedExposureClock.deterministicRoll(window, "decay:uranium");
        assertEquals(first, LoadedExposureClock.deterministicRoll(window, "decay:uranium"));
        assertEquals(
            LoadedExposureClock.deterministicSeed(window, "decay:uranium"),
            LoadedExposureClock.deterministicSeed(window, "decay:uranium")
        );
        assertNotEquals(first, LoadedExposureClock.deterministicRoll(window, "decay:thorium"));
        assertNotEquals(first, LoadedExposureClock.deterministicRoll(new LoadedExposureClock.Window(40L, 60L, 123L), "decay:uranium"));
    }

    @Test
    void negativeAndOverflowDurationsAreBounded() {
        CompoundTag stackTag = new CompoundTag();
        stackTag.getCompound(LoadedExposureClock.TAG_KEY).putLong("t", Long.MAX_VALUE - 1);

        assertEquals(0L, LoadedExposureClock.advance(stackTag, -10L, 1L).endTick());
        CompoundTag clock = new CompoundTag();
        CompoundTag data = new CompoundTag();
        data.putLong("t", Long.MAX_VALUE - 1);
        clock.put(LoadedExposureClock.TAG_KEY, data);
        assertEquals(Long.MAX_VALUE, LoadedExposureClock.advance(clock, 10L, 1L).endTick());
    }
}
