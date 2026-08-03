package com.gerald.latentchemlib.api;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Compact fixed-point isotope composition. An empty vector means natural abundance. */
public final class IsotopeEnsemble {
    public static final int PARTS_PER_MILLION = 1_000_000;

    public enum Binding {
        UNBOUND,
        REVERSIBLE,
        PERMANENT
    }

    private final Map<Integer, Integer> abundancePpm;
    private final Binding binding;

    private IsotopeEnsemble(Map<Integer, Integer> abundancePpm, Binding binding) {
        this.abundancePpm = Collections.unmodifiableMap(new LinkedHashMap<>(abundancePpm));
        this.binding = binding == null ? Binding.UNBOUND : binding;
    }

    public static IsotopeEnsemble natural() {
        return new IsotopeEnsemble(Map.of(), Binding.UNBOUND);
    }

    public static IsotopeEnsemble of(Map<Integer, Double> weights, Binding binding) {
        if (weights == null || weights.isEmpty()) return natural();
        TreeMap<Integer, Double> positive = new TreeMap<>();
        weights.forEach((massNumber, weight) -> {
            if (massNumber != null && massNumber > 0 && weight != null && weight > 0.0 && Double.isFinite(weight)) {
                positive.merge(massNumber, weight, Double::sum);
            }
        });
        double total = positive.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) return natural();

        TreeMap<Integer, Integer> ppm = new TreeMap<>();
        int assigned = 0;
        int last = positive.lastKey();
        for (var entry : positive.entrySet()) {
            int part = entry.getKey() == last
                ? PARTS_PER_MILLION - assigned
                : (int) Math.floor(entry.getValue() * PARTS_PER_MILLION / total);
            if (part > 0) {
                ppm.put(entry.getKey(), part);
                assigned += part;
            }
        }
        if (assigned < PARTS_PER_MILLION && !ppm.isEmpty()) ppm.merge(ppm.lastKey(), PARTS_PER_MILLION - assigned, Integer::sum);
        return ppm.isEmpty() ? natural() : new IsotopeEnsemble(ppm, binding);
    }

    public static IsotopeEnsemble pure(int massNumber, Binding binding) {
        return massNumber <= 0 ? natural() : new IsotopeEnsemble(Map.of(massNumber, PARTS_PER_MILLION), binding);
    }

    public boolean isNatural() { return abundancePpm.isEmpty(); }
    public boolean isPure() { return abundancePpm.size() == 1; }
    public Map<Integer, Integer> abundancePpm() { return abundancePpm; }
    public Binding binding() { return binding; }

    public double fraction(int massNumber) {
        return abundancePpm.getOrDefault(massNumber, 0) / (double) PARTS_PER_MILLION;
    }

    public int select(double unitRoll) {
        if (isNatural()) return 0;
        int target = (int) Math.floor(Math.max(0.0, Math.min(Math.nextDown(1.0), unitRoll)) * PARTS_PER_MILLION);
        int cursor = 0;
        for (var entry : abundancePpm.entrySet()) {
            cursor += entry.getValue();
            if (target < cursor) return entry.getKey();
        }
        return abundancePpm.keySet().stream().reduce((first, second) -> second).orElse(0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("v", (byte) 1);
        tag.putIntArray("a", List.copyOf(abundancePpm.keySet()));
        tag.putIntArray("p", List.copyOf(abundancePpm.values()));
        tag.putByte("b", (byte) binding.ordinal());
        return tag;
    }

    public static IsotopeEnsemble load(CompoundTag tag) {
        int[] masses = tag.getIntArray("a");
        int[] parts = tag.getIntArray("p");
        if (masses.length == 0 || masses.length != parts.length) return natural();
        Map<Integer, Double> weights = new LinkedHashMap<>();
        for (int index = 0; index < masses.length; index++) {
            if (masses[index] > 0 && parts[index] > 0) weights.merge(masses[index], (double) parts[index], Double::sum);
        }
        int ordinal = tag.getByte("b");
        Binding binding = ordinal >= 0 && ordinal < Binding.values().length ? Binding.values()[ordinal] : Binding.UNBOUND;
        return of(weights, binding);
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof IsotopeEnsemble other
            && abundancePpm.equals(other.abundancePpm) && binding == other.binding;
    }

    @Override
    public int hashCode() {
        return 31 * abundancePpm.hashCode() + binding.hashCode();
    }
}
