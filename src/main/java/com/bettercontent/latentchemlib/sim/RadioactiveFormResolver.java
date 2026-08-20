package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.LatentDataManager;
import com.bettercontent.latentchemlib.data.NuclearDecayRule;
import com.bettercontent.latentchemlib.data.NuclearFormRule;
import com.smashingmods.chemlib.api.Element;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.Optional;

/** Resolves isotope-backed ChemLib forms and fixed radioactive family forms. */
public final class RadioactiveFormResolver {
    public static final RadioactiveFormResolver INSTANCE = new RadioactiveFormResolver();

    private RadioactiveFormResolver() {}

    public Optional<ResolvedForm> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return Optional.empty();
        Optional<NuclearFormRule> fixed = LatentDataManager.INSTANCE.nuclearFormRules().stream()
            .filter(NuclearFormRule::fixedProfile)
            .filter(rule -> matchesItem(rule, stack, itemId))
            .max(Comparator.comparingInt(NuclearFormRule::specificity));
        if (fixed.isPresent()) return Optional.of(fixed(itemId.toString(), fixed.get()));
        return resolveLegacy(itemId, stack.getItem());
    }

    public Optional<ResolvedBlock> resolve(BlockState state) {
        if (state == null || state.isAir()) return Optional.empty();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) return Optional.empty();
        return LatentDataManager.INSTANCE.nuclearFormRules().stream()
            .filter(NuclearFormRule::fixedProfile)
            .filter(rule -> matchesBlock(rule, state, blockId))
            .max(Comparator.comparingInt(NuclearFormRule::specificity))
            .map(rule -> new ResolvedBlock(blockId.toString(), fixed(blockId.toString(), rule)));
    }

    Optional<ResolvedForm> resolve(ResourceLocation itemId, Item item) {
        return resolveLegacy(itemId, item);
    }

    private Optional<ResolvedForm> resolveLegacy(ResourceLocation itemId, Item item) {
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
        if (!(item instanceof Element)) return Optional.empty();
        String resolvedChemicalId = canonicalId;
        NuclearDecayRule decay = LatentDataManager.INSTANCE.nuclearDecayRules().stream()
            .filter(candidate -> candidate.inputChemical().equals(resolvedChemicalId))
            .filter(candidate -> candidate.isotopeMassNumber() >= LatentDataManager.INSTANCE.nuclearPhenomenaProfile().decayMinimumIsotopeMassNumber())
            .filter(candidate -> candidate.halfLifeSeconds() > 0.0 && !candidate.outputChemical().isBlank())
            .findFirst().orElse(null);
        if (decay == null) return Optional.empty();
        double unitMass = unitMass(decay.isotopeMassNumber(), materialUnits);
        return Optional.of(new ResolvedForm(itemId.toString(), resolvedChemicalId, decay.isotopeMassNumber(),
            materialUnits, unitMass, "", 0.0, 0.0, false, false));
    }

    private static ResolvedForm fixed(String formId, NuclearFormRule rule) {
        return new ResolvedForm(formId, rule.family(), 0, rule.materialUnits(), rule.materialUnits(),
            rule.family(), rule.radiationStrength(), rule.heatStrength(), rule.naturalWorldgenInert(),
            rule.placedAlwaysActive());
    }

    private static boolean matchesItem(NuclearFormRule rule, ItemStack stack, ResourceLocation id) {
        if (!rule.item().isBlank() && rule.item().equals(id.toString())) return true;
        ResourceLocation tag = ResourceLocation.tryParse(rule.itemTag());
        return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
    }

    private static boolean matchesBlock(NuclearFormRule rule, BlockState state, ResourceLocation id) {
        if (!rule.block().isBlank() && rule.block().equals(id.toString())) return true;
        ResourceLocation tag = ResourceLocation.tryParse(rule.blockTag());
        return tag != null && state.is(TagKey.create(Registries.BLOCK, tag));
    }

    static double unitMass(int isotopeMassNumber, double materialUnits) {
        return Math.max(1, isotopeMassNumber) * Math.max(0.0, materialUnits);
    }

    public record ResolvedForm(String formId, String chemicalId, int isotopeMassNumber, double materialUnits,
        double unitMass, String family, double radiationStrength, double heatStrength,
        boolean naturalWorldgenInert, boolean placedAlwaysActive) {
        public boolean fixedProfile() { return !family.isBlank(); }
    }

    public record ResolvedBlock(String blockId, ResolvedForm form) {}
}
