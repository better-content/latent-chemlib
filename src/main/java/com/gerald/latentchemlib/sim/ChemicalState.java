package com.gerald.latentchemlib.sim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A conserved, versioned chemical mixture.  The scalar fields describe the
 * shared physical state while {@code components} is the authoritative mass
 * ledger.  {@link #chemicalId()} remains as a deterministic dominant-species
 * view for integrations which can only name one chemical.
 */
public final class ChemicalState {
    public static final int STATE_VERSION = 2;
    private static final String AIR = "minecraft:air";

    private final Map<String, Double> components;
    private final double density;
    private final double temperature;
    private final double charge;
    private final double energy;

    public ChemicalState(String chemicalId, double mass, double density, double temperature, double charge, double energy) {
        this(mass > 0.0 ? Map.of(normalizeId(chemicalId), mass) : Map.of(), density, temperature, charge, energy);
    }

    public ChemicalState(Map<String, Double> components, double density, double temperature, double charge, double energy) {
        TreeMap<String, Double> normalized = new TreeMap<>();
        if (components != null) {
            components.forEach((id, mass) -> {
                double bounded = mass == null ? 0.0 : Math.max(0.0, mass);
                if (bounded > 0.0) normalized.merge(normalizeId(id), bounded, Double::sum);
            });
        }
        this.components = Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
        this.density = Math.max(0.0, density);
        this.temperature = Math.max(0.0, temperature);
        this.charge = Math.max(0.0, charge);
        this.energy = Math.max(0.0, energy);
    }

    public static ChemicalState empty() {
        return new ChemicalState(Map.of(), 0.0, 293.0, 0.0, 0.0);
    }

    public static ChemicalState load(CompoundTag tag) {
        if (tag.contains("components", Tag.TAG_LIST)) {
            Map<String, Double> components = new LinkedHashMap<>();
            ListTag entries = tag.getList("components", Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                double mass = entry.getDouble("mass");
                if (mass > 0.0) components.merge(normalizeId(entry.getString("id")), mass, Double::sum);
            }
            return new ChemicalState(
                components,
                tag.getDouble("density"),
                tag.contains("temperature", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("temperature") : 293.0,
                tag.getDouble("charge"),
                tag.getDouble("energy")
            );
        }

        // v1 migration: the old state stored one primary id and one total mass.
        String chemicalId = tag.getString("chemical_id");
        return new ChemicalState(
            chemicalId.isBlank() ? AIR : chemicalId,
            tag.getDouble("mass"),
            tag.getDouble("density"),
            tag.contains("temperature", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("temperature") : 293.0,
            tag.getDouble("charge"),
            tag.getDouble("energy")
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("state_version", STATE_VERSION);
        ListTag entries = new ListTag();
        components.forEach((id, mass) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id);
            entry.putDouble("mass", mass);
            entries.add(entry);
        });
        tag.put("components", entries);
        tag.putDouble("density", density);
        tag.putDouble("temperature", temperature);
        tag.putDouble("charge", charge);
        tag.putDouble("energy", energy);
        return tag;
    }

    public Map<String, Double> components() {
        return components;
    }

    public String chemicalId() {
        String dominant = AIR;
        double dominantMass = 0.0;
        for (Map.Entry<String, Double> component : components.entrySet()) {
            if (component.getValue() > dominantMass) {
                dominant = component.getKey();
                dominantMass = component.getValue();
            }
        }
        return dominant;
    }

    public double mass() {
        return components.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double massOf(String chemicalId) {
        return components.getOrDefault(normalizeId(chemicalId), 0.0);
    }

    public boolean contains(String chemicalId) {
        return massOf(chemicalId) > 0.0;
    }

    public boolean isPure() {
        return components.size() <= 1;
    }

    public double density() { return density; }
    public double temperature() { return temperature; }
    public double charge() { return charge; }
    public double energy() { return energy; }

    public ChemicalState merge(ChemicalState other) {
        if (other == null || other.mass() <= 0.0) return this;
        if (mass() <= 0.0) return other;
        double thisMass = mass();
        double otherMass = other.mass();
        double combinedMass = thisMass + otherMass;
        Map<String, Double> combined = new LinkedHashMap<>(components);
        other.components.forEach((id, value) -> combined.merge(id, value, Double::sum));
        return new ChemicalState(
            combined,
            density + other.density,
            ((temperature * thisMass) + (other.temperature * otherMass)) / combinedMass,
            ((charge * thisMass) + (other.charge * otherMass)) / combinedMass,
            energy + other.energy
        );
    }

    /** Scales every species proportionally to the requested total mass. */
    public ChemicalState withMass(double nextMass) {
        double currentMass = mass();
        double bounded = Math.max(0.0, nextMass);
        double ratio = currentMass <= 0.0 ? 0.0 : bounded / currentMass;
        Map<String, Double> scaled = new LinkedHashMap<>();
        components.forEach((id, value) -> scaled.put(id, value * ratio));
        return new ChemicalState(scaled, density * ratio, temperature, charge, energy * ratio);
    }

    public ChemicalState withEnergy(double nextEnergy) {
        return new ChemicalState(components, density, temperature, charge, Math.max(0.0, nextEnergy));
    }

    public ChemicalState withConditions(double nextTemperature, double nextCharge, double nextEnergy) {
        return new ChemicalState(components, density, nextTemperature, nextCharge, nextEnergy);
    }

    public ChemicalState withPhysicalState(double nextDensity, double nextTemperature, double nextCharge, double nextEnergy) {
        return new ChemicalState(components, nextDensity, nextTemperature, nextCharge, nextEnergy);
    }

    /**
     * Converts all mass of one component to another at a stated mass ratio.
     * Other species remain untouched and concentration follows total mass.
     */
    public ChemicalState transmute(String inputChemical, String outputChemical, double outputMassRatio) {
        String input = normalizeId(inputChemical);
        String output = normalizeId(outputChemical);
        double consumed = components.getOrDefault(input, 0.0);
        if (consumed <= 0.0) return this;
        Map<String, Double> next = new LinkedHashMap<>(components);
        next.remove(input);
        double produced = consumed * Math.max(0.0, outputMassRatio);
        if (produced > 0.0) next.merge(output, produced, Double::sum);
        double currentMass = mass();
        double nextMass = currentMass - consumed + produced;
        double ratio = currentMass <= 0.0 ? 0.0 : nextMass / currentMass;
        return new ChemicalState(next, density * ratio, temperature, charge, energy);
    }

    public Split split(double requestedMass) {
        double moved = Math.min(Math.max(0.0, requestedMass), mass());
        if (moved <= 0.0) return new Split(empty(), this);
        ChemicalState extracted = withMass(moved);
        return new Split(extracted, withMass(mass() - moved));
    }

    public Split splitChemical(String chemicalId, double requestedMass) {
        String id = normalizeId(chemicalId);
        double moved = Math.min(Math.max(0.0, requestedMass), massOf(id));
        if (moved <= 0.0) return new Split(empty(), this);
        double totalMass = mass();
        double fraction = totalMass <= 0.0 ? 0.0 : moved / totalMass;
        Map<String, Double> remainder = new LinkedHashMap<>(components);
        double left = remainder.get(id) - moved;
        if (left > 0.0) remainder.put(id, left); else remainder.remove(id);
        ChemicalState extracted = new ChemicalState(id, moved, density * fraction, temperature, charge, energy * fraction);
        ChemicalState remaining = new ChemicalState(remainder, density * (1.0 - fraction), temperature, charge, energy * (1.0 - fraction));
        return new Split(extracted, remaining);
    }

    public record Split(ChemicalState extracted, ChemicalState remainder) {}

    private static String normalizeId(String id) {
        return id == null || id.isBlank() ? AIR : id;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ChemicalState other)) return false;
        return Double.compare(density, other.density) == 0
            && Double.compare(temperature, other.temperature) == 0
            && Double.compare(charge, other.charge) == 0
            && Double.compare(energy, other.energy) == 0
            && components.equals(other.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(components, density, temperature, charge, energy);
    }

    @Override
    public String toString() {
        return "ChemicalState[components=" + components + ", density=" + density + ", temperature=" + temperature
            + ", charge=" + charge + ", energy=" + energy + ']';
    }
}
