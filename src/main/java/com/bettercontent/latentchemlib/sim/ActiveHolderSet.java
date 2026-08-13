package com.bettercontent.latentchemlib.sim;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Small round-robin index whose work is proportional to active holders and an explicit visit cap. */
final class ActiveHolderSet<T> {
    enum Decision { KEEP, REMOVE, STOP }

    private final Set<T> entries = new LinkedHashSet<>();

    boolean add(T value) { return entries.add(value); }
    boolean remove(T value) { return entries.remove(value); }
    boolean removeIf(Predicate<T> predicate) { return entries.removeIf(predicate); }
    int size() { return entries.size(); }

    int visit(int limit, Function<T, Decision> visitor) {
        int visited = 0;
        int bounded = Math.max(0, limit);
        while (visited < bounded && !entries.isEmpty()) {
            T value = entries.iterator().next();
            entries.remove(value);
            Decision decision = visitor.apply(value);
            visited++;
            if (decision == Decision.KEEP || decision == Decision.STOP) entries.add(value);
            if (decision == Decision.STOP) break;
        }
        return visited;
    }
}
