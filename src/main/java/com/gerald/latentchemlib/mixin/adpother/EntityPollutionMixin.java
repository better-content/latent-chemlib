package com.gerald.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.pollution.EntityPollution;
import com.gerald.latentchemlib.integration.adpother.AdpotherCloudView;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityPollution.class, remap = false)
public abstract class EntityPollutionMixin {
    @Inject(method = "isHeadInPollutant", at = @At("RETURN"), cancellable = true, require = 1)
    private void latentChemlib$recognizeCloudContact(
        Pollutant<?> selector,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValue()) return;
        EntityPollution self = (EntityPollution) (Object) this;
        if (!(self.getWorldLevel() instanceof ServerLevel level)) return;
        boolean matches = AdpotherCloudView.INSTANCE.gasSelectorAt(level, self.getBlockPos())
            .map(selector::equals)
            .orElse(false);
        if (matches) cir.setReturnValue(true);
    }
}
