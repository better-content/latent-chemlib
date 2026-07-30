package com.gerald.latentchemlib.mixin.adpother;

import com.endertech.minecraft.forge.blocks.IEmitter;
import com.endertech.minecraft.mods.adpother.pollution.ChunkPollution;
import com.endertech.minecraft.mods.adpother.sources.Emitter;
import com.endertech.minecraft.mods.adpother.sources.Fuel;
import com.endertech.minecraft.mods.adpother.sources.SourceBase;
import com.gerald.latentchemlib.integration.adpother.AdpotherEmissionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkPollution.class, remap = false)
public abstract class ChunkPollutionMixin {
    @Inject(
        method = "increaseBy(Lcom/endertech/minecraft/mods/adpother/sources/Emitter;Lcom/endertech/minecraft/forge/blocks/IEmitter$Type;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcom/endertech/minecraft/mods/adpother/sources/Fuel;I)V",
        at = @At("HEAD"),
        require = 1
    )
    private void latentChemlib$beginFuelEmission(
        Emitter emitter,
        IEmitter.Type type,
        ServerLevel level,
        BlockPos pos,
        Fuel fuel,
        int amount,
        CallbackInfo ci
    ) {
        AdpotherEmissionContext.push(AdpotherEmissionContext.forEmitter(emitter, fuel));
    }

    @Inject(
        method = "increaseBy(Lcom/endertech/minecraft/mods/adpother/sources/Emitter;Lcom/endertech/minecraft/forge/blocks/IEmitter$Type;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcom/endertech/minecraft/mods/adpother/sources/Fuel;I)V",
        at = @At("RETURN"),
        require = 1
    )
    private void latentChemlib$endFuelEmission(
        Emitter emitter,
        IEmitter.Type type,
        ServerLevel level,
        BlockPos pos,
        Fuel fuel,
        int amount,
        CallbackInfo ci
    ) {
        AdpotherEmissionContext.pop();
    }

    @Inject(
        method = "increaseBy(Lcom/endertech/minecraft/mods/adpother/sources/SourceBase;FLnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
        at = @At("HEAD"),
        require = 1
    )
    private void latentChemlib$beginSourceEmission(
        SourceBase source,
        float factor,
        ServerLevel level,
        BlockPos pos,
        CallbackInfo ci
    ) {
        AdpotherEmissionContext.push(AdpotherEmissionContext.forSource(source));
    }

    @Inject(
        method = "increaseBy(Lcom/endertech/minecraft/mods/adpother/sources/SourceBase;FLnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
        at = @At("RETURN"),
        require = 1
    )
    private void latentChemlib$endSourceEmission(
        SourceBase source,
        float factor,
        ServerLevel level,
        BlockPos pos,
        CallbackInfo ci
    ) {
        AdpotherEmissionContext.pop();
    }
}
