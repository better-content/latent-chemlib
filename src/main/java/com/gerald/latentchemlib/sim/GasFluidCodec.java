package com.gerald.latentchemlib.sim;

import com.smashingmods.chemlib.api.Chemical;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Single conversion boundary between ChemLib gas fluids and Latent chemical state.
 */
public final class GasFluidCodec {
    public static final int MILLIBUCKETS_PER_UNIT = 250;
    public static final double MASS_PER_UNIT = 16.0;
    private static final String STATE_TAG = "latent_chemlib_state";

    private GasFluidCodec() {}

    public static double massForMillibuckets(int amount) {
        return Math.max(0, amount) * MASS_PER_UNIT / MILLIBUCKETS_PER_UNIT;
    }

    public static int millibucketsForMass(double mass) {
        if (!Double.isFinite(mass) || mass <= 0.0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(mass * MILLIBUCKETS_PER_UNIT / MASS_PER_UNIT));
    }

    public static Optional<String> chemicalId(Fluid fluid) {
        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
        if (fluidId == null || !"chemlib".equals(fluidId.getNamespace())) return Optional.empty();
        String path = fluidId.getPath();
        String chemicalPath;
        if (path.endsWith("_fluid")) {
            chemicalPath = path.substring(0, path.length() - "_fluid".length());
        } else if (path.endsWith("_flowing")) {
            chemicalPath = path.substring(0, path.length() - "_flowing".length());
        } else {
            return Optional.empty();
        }
        ResourceLocation itemId = new ResourceLocation("chemlib", chemicalPath);
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        return item instanceof Chemical chemical && GasEscapeHandler.canEscapeAsGas(chemical)
            ? Optional.of(itemId.toString())
            : Optional.empty();
    }

    public static Optional<Fluid> sourceFluid(String chemicalId) {
        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        if (id == null || !"chemlib".equals(id.getNamespace())) return Optional.empty();
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (!(item instanceof Chemical chemical) || !GasEscapeHandler.canEscapeAsGas(chemical)) return Optional.empty();
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id.getNamespace(), id.getPath() + "_fluid"));
        return fluid == null || fluid == Fluids.EMPTY ? Optional.empty() : Optional.of(fluid);
    }

    public static boolean isGasFluid(Fluid fluid) {
        return chemicalId(fluid).isPresent();
    }

    public static Optional<ChemicalState> stateFromFluid(FluidStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        Optional<String> chemicalId = chemicalId(stack.getFluid());
        if (chemicalId.isEmpty()) return Optional.empty();
        double mass = massForMillibuckets(stack.getAmount());
        if (mass <= 0.0) return Optional.empty();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(STATE_TAG, CompoundTag.TAG_COMPOUND)) {
            ChemicalState tagged = ChemicalState.load(tag.getCompound(STATE_TAG));
            if (chemicalId.get().equals(tagged.chemicalId()) && tagged.mass() > 0.0) {
                return Optional.of(tagged.withMass(mass));
            }
        }
        return Optional.of(new ChemicalState(
            chemicalId.get(),
            mass,
            mass / MASS_PER_UNIT,
            293.0,
            0.0,
            0.0
        ));
    }

    public static FluidStack fluidFromState(ChemicalState state, int requestedAmount) {
        if (state.mass() <= 0.0 || requestedAmount <= 0) return FluidStack.EMPTY;
        Optional<Fluid> fluid = sourceFluid(state.chemicalId());
        if (fluid.isEmpty()) return FluidStack.EMPTY;
        int amount = Math.min(requestedAmount, millibucketsForMass(state.mass()));
        if (amount <= 0) return FluidStack.EMPTY;
        FluidStack stack = new FluidStack(fluid.get(), amount);
        stack.getOrCreateTag().put(STATE_TAG, state.withMass(massForMillibuckets(amount)).save());
        return stack;
    }
}
