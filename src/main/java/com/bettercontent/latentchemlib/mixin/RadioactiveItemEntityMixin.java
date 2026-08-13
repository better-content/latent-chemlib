package com.bettercontent.latentchemlib.mixin;

import com.bettercontent.latentchemlib.sim.NuclearSimulationService;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ChemLib radioactive matter remains recoverable and transportable in molten rock. */
@Mixin(ItemEntity.class)
public abstract class RadioactiveItemEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void latentChemlib$surviveFire(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        ItemEntity self = (ItemEntity) (Object) this;
        // Lava can leave an entity burning for several ticks after the flow
        // carries it out of the fluid cell, so the whole fire-damage tail must
        // remain suppressed for configured radioactive matter.
        if (source.is(DamageTypeTags.IS_FIRE) && NuclearSimulationService.INSTANCE.isNuclearRelevant(self.getItem())) {
            callback.setReturnValue(false);
        }
    }
}
