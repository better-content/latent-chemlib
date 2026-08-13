package com.bettercontent.latentchemlib.api;

import com.bettercontent.latentchemlib.data.LatentDataManager;
import com.smashingmods.chemlib.api.Chemical;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Public NBT boundary for isotope-bearing ChemLib forms. */
public final class IsotopeItemData {
    public static final String TAG_KEY = "latent_chemlib_isotopes";

    private IsotopeItemData() {}

    public static boolean supports(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof Chemical;
    }

    /** Returns the compact explicit vector; absent NBT is the natural sentinel. */
    public static IsotopeEnsemble explicit(ItemStack stack) {
        if (stack == null || !stack.hasTag() || !stack.getOrCreateTag().contains(TAG_KEY)) return IsotopeEnsemble.natural();
        return IsotopeEnsemble.load(stack.getOrCreateTag().getCompound(TAG_KEY));
    }

    /** Resolves natural composition from the live datapack catalogue without writing it to the stack. */
    public static IsotopeEnsemble resolved(ItemStack stack) {
        IsotopeEnsemble explicit = explicit(stack);
        if (!explicit.isNatural()) return explicit;
        ResourceLocation id = stack == null || stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? explicit : LatentDataManager.INSTANCE.isotopeCatalog().naturalEnsemble(id.toString());
    }

    public static boolean set(ItemStack stack, IsotopeEnsemble ensemble) {
        if (!supports(stack) || ensemble == null) return false;
        IsotopeEnsemble existing = explicit(stack);
        if (existing.binding() == IsotopeEnsemble.Binding.PERMANENT && !existing.equals(ensemble)) return false;
        if (ensemble.isNatural()) {
            stack.removeTagKey(TAG_KEY);
        } else {
            stack.getOrCreateTag().put(TAG_KEY, ensemble.save());
        }
        return true;
    }

    public static boolean clear(ItemStack stack) {
        IsotopeEnsemble existing = explicit(stack);
        if (existing.binding() == IsotopeEnsemble.Binding.PERMANENT) return false;
        stack.removeTagKey(TAG_KEY);
        return true;
    }
}
