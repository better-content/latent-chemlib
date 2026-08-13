package com.bettercontent.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.pollution.PointPollution;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherCloudView;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PointPollution.class, remap = false)
public abstract class PointPollutionMixin {
    @Inject(method = "update", at = @At("RETURN"), require = 1)
    private void latentChemlib$includeClouds(CallbackInfo ci) {
        PointPollution self = (PointPollution) (Object) this;
        if (!(self.getWorldLevel() instanceof ServerLevel level)) return;
        AdpotherCloudView.INSTANCE.contactAt(level, self.getBlockPos(), self.getPosition())
            .ifPresent(contact ->
                self.getOrCreateInfoFor(contact.selector()).setQuantity(contact.units())
            );
    }
}
