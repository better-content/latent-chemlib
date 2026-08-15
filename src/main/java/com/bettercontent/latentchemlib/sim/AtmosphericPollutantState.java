package com.bettercontent.latentchemlib.sim;

import net.minecraft.nbt.CompoundTag;

/** The deliberately small, AdPother-native state stored by an atmospheric cell. */
public record AtmosphericPollutantState(String pollutantId, int units) {
    public static final int VERSION = 1;
    public static final AtmosphericPollutantState EMPTY = new AtmosphericPollutantState("", 0);

    public AtmosphericPollutantState {
        pollutantId = pollutantId == null ? "" : pollutantId;
        units = Math.max(0, units);
        if (pollutantId.isBlank() || units == 0) {
            pollutantId = "";
            units = 0;
        }
    }

    public boolean isEmpty() {
        return pollutantId.isBlank() || units <= 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putString("pollutant", pollutantId);
        tag.putInt("units", units);
        return tag;
    }

    public static AtmosphericPollutantState load(CompoundTag tag) {
        if (tag == null || tag.getInt("version") != VERSION) return EMPTY;
        return new AtmosphericPollutantState(tag.getString("pollutant"), tag.getInt("units"));
    }
}
