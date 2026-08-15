package com.bettercontent.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.pollution.EntityPollution;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherCloudView;
import net.minecraft.server.level.ServerLevel;
import com.endertech.minecraft.forge.math.Percentage;
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

    @Inject(method = "getInfluenceOf", at = @At("RETURN"), cancellable = true, require = 1)
    private void latentChemlib$useCellConcentration(
        Pollutant<?> selector,
        CallbackInfoReturnable<Percentage> cir
    ) {
        EntityPollution self = (EntityPollution) (Object) this;
        if (!(self.getWorldLevel() instanceof ServerLevel level)) return;
        AdpotherCloudView.INSTANCE.contactAt(level, self.getBlockPos(), self.getPosition()).ifPresent(contact -> {
            if (!contact.selector().equals(selector)) return;
            Percentage cell = Percentage.from(contact.units(), Math.max(1, selector.getPollutionCapacity()));
            if (cell.compareTo(cir.getReturnValue()) > 0) cir.setReturnValue(cell);
        });
    }
}
