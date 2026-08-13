package com.bettercontent.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.emissions.DelayedTileEmission;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherEmissionContext;
import com.bettercontent.latentchemlib.integration.adpother.DelayedEmissionContextStore;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(value = DelayedTileEmission.class, remap = false)
public abstract class DelayedTileEmissionMixin {
    @Redirect(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lcom/endertech/minecraft/mods/adpother/blocks/Pollutant;emitFrom(Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/util/Set;I)I"
        ),
        require = 1
    )
    private int latentChemlib$routeWithPreservedState(
        Pollutant<?> pollutant,
        BlockEntity blockEntity,
        Set<BlockState> relatedBlocks,
        int amount
    ) {
        AdpotherEmissionContext context = DelayedEmissionContextStore.INSTANCE
            .contextFor(blockEntity, pollutant)
            .orElse(AdpotherEmissionContext.AMBIENT);
        int accepted = AdpotherEmissionContext.callWith(
            context,
            () -> pollutant.emitFrom(blockEntity, relatedBlocks, amount)
        );
        DelayedEmissionContextStore.INSTANCE.consume(blockEntity, pollutant, accepted);
        return accepted;
    }
}
