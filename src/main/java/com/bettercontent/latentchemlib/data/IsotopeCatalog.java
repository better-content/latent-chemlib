package com.bettercontent.latentchemlib.data;

import com.bettercontent.latentchemlib.api.IsotopeEnsemble;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable enumerable view of every isotope known to the loaded datapacks. */
public final class IsotopeCatalog {
    private final List<IsotopeDefinition> definitions;
    private final Map<String, List<IsotopeDefinition>> byElement;

    public IsotopeCatalog(List<IsotopeDefinition> definitions) {
        List<IsotopeDefinition> sorted = new ArrayList<>(definitions == null ? List.of() : definitions);
        sorted.sort(Comparator.comparing(IsotopeDefinition::elementId).thenComparingInt(IsotopeDefinition::massNumber));
        this.definitions = List.copyOf(sorted);
        Map<String, List<IsotopeDefinition>> grouped = new LinkedHashMap<>();
        for (IsotopeDefinition definition : sorted) grouped.computeIfAbsent(definition.elementId(), ignored -> new ArrayList<>()).add(definition);
        grouped.replaceAll((ignored, values) -> List.copyOf(values));
        this.byElement = Map.copyOf(grouped);
    }

    public static IsotopeCatalog empty() { return new IsotopeCatalog(List.of()); }
    public List<IsotopeDefinition> allKnown() { return definitions; }
    public List<IsotopeDefinition> knownFor(String elementId) { return byElement.getOrDefault(elementId, List.of()); }

    public Optional<IsotopeDefinition> find(String elementId, int massNumber) {
        return knownFor(elementId).stream().filter(value -> value.massNumber() == massNumber).findFirst();
    }

    public IsotopeEnsemble naturalEnsemble(String elementId) {
        Map<Integer, Double> weights = new LinkedHashMap<>();
        for (IsotopeDefinition definition : knownFor(elementId)) {
            if (definition.naturalAbundance() > 0.0) weights.put(definition.massNumber(), definition.naturalAbundance());
        }
        return IsotopeEnsemble.of(weights, IsotopeEnsemble.Binding.UNBOUND);
    }
}
