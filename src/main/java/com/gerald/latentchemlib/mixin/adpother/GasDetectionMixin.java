package com.gerald.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.pollution.GasDetection;
import com.gerald.latentchemlib.integration.adpother.AdpotherCloudView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GasDetection.class, remap = false)
public abstract class GasDetectionMixin {
    @Shadow protected Entity entity;
    @Shadow protected int radius;

    @Inject(method = "getResult", at = @At("RETURN"), cancellable = true, require = 1)
    private void latentChemlib$includeClouds(CallbackInfoReturnable<GasDetection.Result> cir) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        GasDetection.Result original = cir.getReturnValue();
        AdpotherCloudView.Detection clouds =
            AdpotherCloudView.INSTANCE.detectionAround(level, entity.blockPosition(), radius);
        cir.setReturnValue(new GasDetection.Result(
            original.explosionRisk() || clouds.explosionRisk(),
            original.gasBlocksAround() + clouds.gasBlocks()
        ));
    }
}
