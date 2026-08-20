package com.bettercontent.latentchemlib.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisturbedRadioactiveDataTest {
    @Test
    void disturbedPlacementSurvivesSavedDataRoundTrip() {
        BlockPos pos = new BlockPos(12, -24, 33);
        var form = new RadioactiveFormResolver.ResolvedForm(
            "realistic_ores:uranium_ore", "uranium", 0, 1.0, 1.0,
            "uranium", 12.0, 3.0, true, true
        );
        var resolved = new RadioactiveFormResolver.ResolvedBlock("realistic_ores:uranium_ore", form);
        DisturbedRadioactiveData original = new DisturbedRadioactiveData();
        original.put(pos, resolved);

        DisturbedRadioactiveData restored = DisturbedRadioactiveData.load(original.save(new CompoundTag()));
        assertTrue(restored.matches(pos, resolved));
        assertEquals("uranium", restored.get(pos).orElseThrow().family());
        assertEquals(12.0, restored.get(pos).orElseThrow().radiationStrength());
        assertEquals(3.0, restored.get(pos).orElseThrow().heatStrength());
    }
}
