package com.gerald.latentchemlib.item;

import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.GasFluidStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ChemicalCellItem extends Item {
    private static final String STATE_KEY = "chemical_state";
    public static final int FLUID_CAPACITY = 4_000;

    public ChemicalCellItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean hasState(ItemStack stack) {
        return stack.hasTag() && stack.getOrCreateTag().contains(STATE_KEY);
    }

    public static ChemicalState state(ItemStack stack) {
        return hasState(stack) ? ChemicalState.load(stack.getOrCreateTag().getCompound(STATE_KEY)) : ChemicalState.empty();
    }

    public static ItemStack withState(ItemStack stack, ChemicalState state) {
        ItemStack copy = stack.copy();
        setState(copy, state);
        return copy;
    }

    public static void setState(ItemStack stack, ChemicalState state) {
        if (state.mass() <= 0.0) {
            stack.removeTagKey(STATE_KEY);
        } else {
            stack.getOrCreateTag().put(STATE_KEY, state.save());
        }
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new CellFluidCapability(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasState(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ChemicalState state = state(stack);
        if (state.mass() <= 0.0) {
            tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.literal(state.chemicalId()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.mass", format(state.mass())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.temperature", format(state.temperature())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.density", format(state.density())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.charge", format(state.charge())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.latent_chemlib.sealed_chemical_cell.energy", format(state.energy())).withStyle(ChatFormatting.GRAY));
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    private static final class CellFluidCapability implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final LazyOptional<IFluidHandlerItem> capability;

        private CellFluidCapability(ItemStack stack) {
            capability = LazyOptional.of(() -> new CellFluidHandler(stack));
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.FLUID_HANDLER_ITEM ? capability.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return new CompoundTag();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            // State is stored on the ItemStack itself so normal stack serialization remains authoritative.
        }
    }

    private static final class CellFluidHandler extends GasFluidStorage implements IFluidHandlerItem {
        private final ItemStack container;

        private CellFluidHandler(ItemStack container) {
            super(
                () -> ChemicalCellItem.state(container),
                state -> ChemicalCellItem.setState(container, state),
                () -> FLUID_CAPACITY,
                () -> true,
                () -> true
            );
            this.container = container;
        }

        @Override
        public ItemStack getContainer() {
            return container;
        }
    }
}
