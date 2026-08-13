package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.api.IsotopeEnsemble;
import com.bettercontent.latentchemlib.api.IsotopeItemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Per-material-unit decay ledger; copying/splitting a stack cannot duplicate mass. */
public final class NuclearStackData {
    public static final String STATE_KEY = "latent_chemlib_nuclear_unit_state";
    public static final String PROVENANCE_KEY = "latent_chemlib_nuclear_provenance";
    public static final String ISOTOPES_KEY = "latent_chemlib_nuclear_isotopes";
    private NuclearStackData() {}

    public static ChemicalState state(ItemStack stack, RadioactiveFormResolver.ResolvedForm form) {
        CompoundTag tag = stack.getOrCreateTag();
        ChemicalState initial = peekState(stack, form);
        if (tag.contains(STATE_KEY)) return initial;
        setState(stack, initial);
        bindIdentity(stack, form.formId(), form.isotopeMassNumber());
        return initial;
    }

    public static ChemicalState peekState(ItemStack stack, RadioactiveFormResolver.ResolvedForm form) {
        CompoundTag tag = stack.getTag();
        ChemicalState state = tag != null && tag.contains(STATE_KEY)
            ? ChemicalState.load(tag.getCompound(STATE_KEY))
            : new ChemicalState(form.chemicalId(), form.unitMass(), form.materialUnits(), 293.0, 0.0, 0.0);
        if (state.explicitIsotopesOf(form.chemicalId()).isPresent()) return state;
        IsotopeEnsemble ensemble = IsotopeItemData.explicit(stack);
        if (ensemble.isNatural() && tag != null && tag.contains(ISOTOPES_KEY)) {
            ensemble = IsotopeEnsemble.load(tag.getCompound(ISOTOPES_KEY));
        }
        if (ensemble.isNatural()) {
            ensemble = IsotopeEnsemble.pure(form.isotopeMassNumber(), IsotopeEnsemble.Binding.PERMANENT);
        }
        java.util.Map<String, IsotopeEnsemble> identities = new java.util.LinkedHashMap<>(state.componentIsotopes());
        identities.put(form.chemicalId(), ensemble);
        return state.withComponentIsotopes(identities);
    }

    public static void setState(ItemStack stack, ChemicalState state) {
        stack.getOrCreateTag().put(STATE_KEY, state.save());
    }

    public static void bindIdentity(ItemStack stack, String provenance, int isotopeMassNumber) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(PROVENANCE_KEY)) tag.putString(PROVENANCE_KEY, provenance);
        if (!tag.contains(ISOTOPES_KEY)) {
            IsotopeEnsemble ensemble = IsotopeItemData.explicit(stack);
            if (ensemble.isNatural()) ensemble = IsotopeEnsemble.pure(isotopeMassNumber, IsotopeEnsemble.Binding.PERMANENT);
            tag.put(ISOTOPES_KEY, ensemble.save());
        }
    }

    public static void syncIdentity(ItemStack stack, ChemicalState state) {
        CompoundTag tag = stack.getOrCreateTag();
        IsotopeEnsemble summary = state.isotopesOf(state.chemicalId());
        tag.put(ISOTOPES_KEY, summary.save());
        if (!summary.isNatural()) tag.put(IsotopeItemData.TAG_KEY, summary.save());
        else tag.remove(IsotopeItemData.TAG_KEY);
    }

    public static String provenance(ItemStack stack) {
        return stack.hasTag() ? stack.getOrCreateTag().getString(PROVENANCE_KEY) : "";
    }

    public static IsotopeEnsemble isotopes(ItemStack stack) {
        return stack.hasTag() && stack.getOrCreateTag().contains(ISOTOPES_KEY)
            ? IsotopeEnsemble.load(stack.getOrCreateTag().getCompound(ISOTOPES_KEY))
            : IsotopeEnsemble.natural();
    }
}
