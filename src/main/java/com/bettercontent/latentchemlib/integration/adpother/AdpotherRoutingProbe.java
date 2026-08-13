package com.bettercontent.latentchemlib.integration.adpother;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Scoped observation seam for proving native AdPother/Advanced Chimneys routing in Forge GameTests.
 * Normal runtime calls pay only an empty thread-local lookup and retain no route history.
 */
public final class AdpotherRoutingProbe {
    private static final ThreadLocal<Deque<Consumer<RouteEvent>>> OBSERVERS = new ThreadLocal<>();

    private AdpotherRoutingProbe() {}

    public static int observe(Consumer<RouteEvent> observer, IntSupplier action) {
        Objects.requireNonNull(observer, "observer");
        Objects.requireNonNull(action, "action");
        Deque<Consumer<RouteEvent>> observers = OBSERVERS.get();
        if (observers == null) {
            observers = new ArrayDeque<>();
            OBSERVERS.set(observers);
        }
        observers.push(observer);
        try {
            return action.getAsInt();
        } finally {
            observers.pop();
            if (observers.isEmpty()) OBSERVERS.remove();
        }
    }

    public static void record(BlockPos outlet, int requested, int filtered, int emitted) {
        Deque<Consumer<RouteEvent>> observers = OBSERVERS.get();
        if (observers == null || observers.isEmpty()) return;
        observers.peek().accept(new RouteEvent(outlet.immutable(), requested, filtered, emitted));
    }

    public record RouteEvent(BlockPos outlet, int requested, int filtered, int emitted) {}
}
