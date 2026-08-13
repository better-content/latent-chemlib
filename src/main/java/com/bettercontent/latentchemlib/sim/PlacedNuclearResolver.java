package com.bettercontent.latentchemlib.sim;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/** Resolves foreign placed forms to the same data-driven material identity used by item stacks. */
public final class PlacedNuclearResolver {
    public static final PlacedNuclearResolver INSTANCE = new PlacedNuclearResolver();

    private PlacedNuclearResolver() {}

    public Optional<ResolvedPlacement> resolve(BlockState state) {
        if (state == null || state.isAir()) return Optional.empty();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) return Optional.empty();

        Item blockItem = state.getBlock().asItem();
        if (blockItem != Items.AIR) {
            Optional<RadioactiveFormResolver.ResolvedForm> form =
                RadioactiveFormResolver.INSTANCE.resolve(new ItemStack(blockItem));
            if (form.isPresent()) return Optional.of(new ResolvedPlacement(blockId.toString(), form.get(), false));
        }

        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && fluid.isSource()) {
            Item bucket = fluid.getType().getBucket();
            if (bucket != Items.AIR) {
                Optional<RadioactiveFormResolver.ResolvedForm> form =
                    RadioactiveFormResolver.INSTANCE.resolve(new ItemStack(bucket));
                if (form.isPresent()) return Optional.of(new ResolvedPlacement(blockId.toString(), form.get(), true));
            }
        }
        return Optional.empty();
    }

    public boolean matches(BlockState state, PlacedNuclearData.Entry entry) {
        return resolve(state).map(resolved ->
            resolved.blockId().equals(entry.blockId())
                && resolved.form().formId().equals(entry.formId())
                && resolved.nativePhase() == entry.nativePhase()
        ).orElse(false);
    }

    public record ResolvedPlacement(
        String blockId,
        RadioactiveFormResolver.ResolvedForm form,
        boolean nativePhase
    ) {}
}
