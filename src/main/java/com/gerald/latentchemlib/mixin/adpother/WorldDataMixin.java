package com.gerald.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.pollution.WorldData;
import com.endertech.minecraft.mods.adpother.sources.Emitter;
import com.gerald.latentchemlib.integration.adpother.AdpotherEmissionContext;
import com.gerald.latentchemlib.integration.adpother.DelayedEmissionContextStore;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldData.class, remap = false)
public abstract class WorldDataMixin {
    @Inject(method = "scheduleEmissionFor", at = @At("HEAD"), require = 1)
    private static void latentChemlib$rememberDelayedState(
        BlockEntity blockEntity,
        Emitter emitter,
        Pollutant<?> pollutant,
        int amount,
        CallbackInfo ci
    ) {
        AdpotherEmissionContext.current().ifPresent(context ->
            DelayedEmissionContextStore.INSTANCE.record(blockEntity, pollutant, amount, context)
        );
    }
}
