package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.LatentDataManager;
import com.bettercontent.latentchemlib.data.NuclearDecayRule;
import com.bettercontent.latentchemlib.data.NuclearFormRule;
import com.smashingmods.chemlib.api.Element;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.Optional;

/** Resolves shape/container registrations to isotope physics without element-name allowlists. */
public final class RadioactiveFormResolver {
    public static final RadioactiveFormResolver INSTANCE = new RadioactiveFormResolver();

    private RadioactiveFormResolver() {}

    public Optional<ResolvedForm> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId == null ? Optional.empty() : resolve(itemId, stack.getItem());
    }

    Optional<ResolvedForm> resolve(ResourceLocation itemId, Item item) {
        String canonicalId = itemId.toString();
        double materialUnits = 1.0;
        if (!(item instanceof Element)) {
            Optional<NuclearFormRule> mapping = LatentDataManager.INSTANCE.nuclearFormRules().stream()
                .filter(rule -> !rule.suffix().isBlank() && itemId.getPath().endsWith(rule.suffix()))
                .max(Comparator.comparingInt(rule -> rule.suffix().length()));
            if (mapping.isEmpty()) return Optional.empty();
            NuclearFormRule rule = mapping.get();
            String basePath = itemId.getPath().substring(0, itemId.getPath().length() - rule.suffix().length());
            ResourceLocation baseId = ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), basePath);
            Item baseItem = ForgeRegistries.ITEMS.getValue(baseId);
            if (!(baseItem instanceof Element)) return Optional.empty();
            canonicalId = baseId.toString();
            materialUnits = rule.materialUnits();
            item = baseItem;
        }
        if (!(item instanceof Element element)) return Optional.empty();
        String resolvedChemicalId = canonicalId;
        NuclearDecayRule decay = LatentDataManager.INSTANCE.nuclearDecayRules().stream()
            .filter(candidate -> candidate.inputChemical().equals(resolvedChemicalId))
            .filter(candidate -> candidate.isotopeMassNumber() >= LatentDataManager.INSTANCE.nuclearPhenomenaProfile().decayMinimumIsotopeMassNumber())
            .filter(candidate -> candidate.halfLifeSeconds() > 0.0 && !candidate.outputChemical().isBlank())
            .findFirst().orElse(null);
        if (decay == null) return Optional.empty();
        double unitMass = unitMass(decay.isotopeMassNumber(), materialUnits);
        return Optional.of(new ResolvedForm(itemId.toString(), resolvedChemicalId, decay.isotopeMassNumber(), materialUnits, unitMass));
    }

    static double unitMass(int isotopeMassNumber, double materialUnits) {
        return Math.max(1, isotopeMassNumber) * Math.max(0.0, materialUnits);
    }

    public record ResolvedForm(String formId, String chemicalId, int isotopeMassNumber, double materialUnits, double unitMass) {}
}
