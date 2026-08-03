package com.gerald.latentchemlib.sim;

import net.minecraft.nbt.CompoundTag;

/** A monotonic clock advanced only by active loaded-holder evaluation. */
public final class LoadedExposureClock {
    public static final String TAG_KEY = "latent_chemlib_loaded_exposure";

    private LoadedExposureClock() {}

    public static Window advance(CompoundTag stackTag, long elapsedTicks, long seedCandidate) {
        CompoundTag clock = stackTag.getCompound(TAG_KEY);
        long start = Math.max(0L, clock.getLong("t"));
        long bounded = Math.max(0L, elapsedTicks);
        long end = start > Long.MAX_VALUE - bounded ? Long.MAX_VALUE : start + bounded;
        long seed = clock.contains("s") ? clock.getLong("s") : mix(seedCandidate);
        clock.putLong("t", end);
        clock.putLong("s", seed);
        stackTag.put(TAG_KEY, clock);
        return new Window(start, end, seed);
    }

    public static double deterministicRoll(Window window, String channel) {
        long bits = deterministicSeed(window, channel);
        return (bits >>> 11) * 0x1.0p-53;
    }

    public static long deterministicSeed(Window window, String channel) {
        long channelHash = channel == null ? 0L : channel.hashCode();
        return mix(window.seed() ^ Long.rotateLeft(window.startTick(), 17) ^ window.endTick() ^ channelHash);
    }

    private static long mix(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record Window(long startTick, long endTick, long seed) {}
}
