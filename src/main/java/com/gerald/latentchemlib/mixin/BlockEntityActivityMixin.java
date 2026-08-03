package com.gerald.latentchemlib.mixin;

import com.gerald.latentchemlib.sim.NuclearSurfaceScanner;
import com.gerald.latentchemlib.sim.GasEscapeHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Converts inventory mutation into local scheduling without surveying loaded chunks. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityActivityMixin {
    @Inject(method = "setChanged()V", at = @At("TAIL"))
    private void latentChemlib$markNuclearHolder(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        NuclearSurfaceScanner.markActive(self);
        GasEscapeHandler.markActive(self);
    }
}
