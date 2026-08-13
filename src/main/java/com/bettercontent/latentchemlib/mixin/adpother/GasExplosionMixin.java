package com.bettercontent.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.pollution.GasExplosion;
import com.bettercontent.latentchemlib.integration.adpother.LatentGasHazardService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GasExplosion.class, remap = false)
public abstract class GasExplosionMixin {
    @Shadow protected LevelAccessor level;
    @Shadow protected Iterable<BlockPos> positions;

    @Inject(method = "tryTrigger", at = @At("HEAD"), cancellable = true, require = 1)
    private void latentChemlib$igniteClouds(CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ServerLevel serverLevel
            && LatentGasHazardService.INSTANCE.tryIgniteAtAny(serverLevel, positions)) {
            cir.setReturnValue(true);
        }
    }
}
