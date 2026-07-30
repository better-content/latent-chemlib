package com.gerald.latentchemlib.mixin.adpother;

import com.endertech.minecraft.mods.adpother.items.VacuumTube;
import com.endertech.minecraft.mods.adpother.items.VacuumBag;
import com.endertech.minecraft.forge.entities.ForgeEntity;
import com.gerald.latentchemlib.integration.adpother.AdpotherCloudView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VacuumTube.BlockSuckInMsg.class, remap = false)
public abstract class VacuumBlockSuckInMixin {
    @Shadow public BlockPos pos;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 1)
    private void latentChemlib$suckCloud(
        Level level,
        VacuumTube tube,
        ServerPlayer player,
        CallbackInfo ci
    ) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        AdpotherCloudView.INSTANCE.gasSelectorAt(serverLevel, pos).ifPresent(selector -> {
            boolean capturedAsWaste = false;
            boolean hasBag = false;
            for (ItemStack equipment : ForgeEntity.getEquipmentOn(player)) {
                if (!(equipment.getItem() instanceof VacuumBag bag)) continue;
                hasBag = true;
                if (bag.fill(equipment, selector, 1) > 0) {
                    capturedAsWaste = true;
                    break;
                }
            }
            if (capturedAsWaste) {
                selector.spend(serverLevel, pos);
                player.displayClientMessage(
                    Component.translatable("message.latent_chemlib.vacuum.captured"),
                    true
                );
            } else {
                player.displayClientMessage(
                    Component.translatable(
                        hasBag
                            ? "message.latent_chemlib.vacuum.full"
                            : "message.latent_chemlib.vacuum.requires_bag"
                    ),
                    true
                );
            }
            ci.cancel();
        });
    }
}
