package com.gerald.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.gerald.latentchemlib.integration.adpother.AdpotherAtmosphereBridge;
import com.gerald.latentchemlib.integration.adpother.AdpotherRoutingProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Pollutant.class, remap = false)
public abstract class PollutantMixin {
    @Shadow
    protected abstract int pumpActiveFilters(LevelAccessor level, BlockPos pos, int amount);

    @Inject(method = "generateAt", at = @At("HEAD"), cancellable = true, require = 1)
    private void latentChemlib$generateAt(
        WorldGenLevel level,
        BlockPos pos,
        int amount,
        int distance,
        CallbackInfoReturnable<Integer> cir
    ) {
        cir.setReturnValue(AdpotherAtmosphereBridge.INSTANCE.emit(self(), level, pos, amount));
    }

    @Inject(method = "pump(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;I)I",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void latentChemlib$pump(
        LevelAccessor level,
        BlockPos pos,
        int amount,
        CallbackInfoReturnable<Integer> cir
    ) {
        int filtered = pumpActiveFilters(level, pos, amount);
        int emitted = AdpotherAtmosphereBridge.INSTANCE.emit(self(), level, pos, amount - filtered);
        AdpotherRoutingProbe.record(pos, amount, filtered, emitted);
        cir.setReturnValue(filtered + emitted);
    }

    @Inject(method = "pumpEntitiesAt", at = @At("HEAD"), cancellable = true, require = 1)
    private void latentChemlib$pumpEntitiesAt(
        LevelAccessor level,
        BlockPos pos,
        int amount,
        CallbackInfoReturnable<Integer> cir
    ) {
        int filtered = pumpActiveFilters(level, pos, amount);
        int emitted = AdpotherAtmosphereBridge.INSTANCE.emit(self(), level, pos, amount - filtered);
        AdpotherRoutingProbe.record(pos, amount, filtered, emitted);
        cir.setReturnValue(filtered + emitted);
    }

    @Inject(method = "spend(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;I)I",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void latentChemlib$spend(
        LevelAccessor level,
        BlockPos pos,
        int amount,
        CallbackInfoReturnable<Integer> cir
    ) {
        cir.setReturnValue(AdpotherAtmosphereBridge.INSTANCE.extract(self(), level, pos, amount));
    }

    @Inject(method = "spendEntitiesAt", at = @At("HEAD"), cancellable = true, require = 1)
    private void latentChemlib$spendEntitiesAt(
        LevelAccessor level,
        BlockPos pos,
        int amount,
        CallbackInfoReturnable<Integer> cir
    ) {
        cir.setReturnValue(AdpotherAtmosphereBridge.INSTANCE.extract(self(), level, pos, amount));
    }

    @SuppressWarnings("unchecked")
    private Pollutant<?> self() {
        return (Pollutant<?>) (Object) this;
    }
}
