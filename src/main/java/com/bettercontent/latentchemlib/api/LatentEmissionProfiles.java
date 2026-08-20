package com.bettercontent.latentchemlib.api;

import com.bettercontent.latentchemlib.sim.DisturbedRadioactiveData;
import com.bettercontent.latentchemlib.sim.RadioactiveFormResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Public read-only fixed-form emission API. It never initializes isotope or disturbance state. */
public final class LatentEmissionProfiles {
    public static final int MAX_STACK_SCALE = 64;

    private LatentEmissionProfiles() {}

    public static Optional<EmissionProfile> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return RadioactiveFormResolver.INSTANCE.resolve(stack)
            .filter(RadioactiveFormResolver.ResolvedForm::fixedProfile)
            .map(form -> active(form, stackScale(stack.getCount())));
    }

    public static Optional<EmissionProfile> resolvePlaced(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return Optional.empty();
        var resolved = RadioactiveFormResolver.INSTANCE.resolve(level.getBlockState(pos));
        if (resolved.isEmpty()) return Optional.empty();
        var form = resolved.get().form();
        boolean disturbed = DisturbedRadioactiveData.get(level).matches(pos, resolved.get());
        boolean active = disturbed || !form.naturalWorldgenInert();
        return Optional.of(active ? active(form, 1) : EmissionProfile.inert(form.family()));
    }

    private static EmissionProfile active(RadioactiveFormResolver.ResolvedForm form, int scale) {
        return new EmissionProfile(form.family(), true,
            form.radiationStrength() * scale, form.heatStrength() * scale);
    }

    static int stackScale(int count) {
        return Math.min(MAX_STACK_SCALE, Math.max(1, count));
    }
}
