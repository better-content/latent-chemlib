package com.bettercontent.latentchemlib.mixin;

import com.bettercontent.latentchemlib.sim.GasEscapeHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
abstract class BlockEntityMutationMixin {
    @Inject(method = "setChanged()V", at = @At("TAIL"))
    private void latentChemlib$queueGasEscapeScan(CallbackInfo callback) {
        GasEscapeHandler.markActive((BlockEntity) (Object) this);
    }
}
