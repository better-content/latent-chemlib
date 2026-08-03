package com.gerald.latentchemlib.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/** Attaches the position sidecar to the actual native self-drop, then atomically consumes it. */
public final class PlacedNuclearLootModifier extends LootModifier {
    public static final Codec<PlacedNuclearLootModifier> CODEC = RecordCodecBuilder.create(instance ->
        codecStart(instance).apply(instance, PlacedNuclearLootModifier::new)
    );

    public PlacedNuclearLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (state == null || origin == null) return loot;
        BlockPos pos = BlockPos.containing(origin);
        PlacedNuclearData data = PlacedNuclearData.get(context.getLevel());
        PlacedNuclearData.Entry entry = data.get(pos).orElse(null);
        if (entry == null || !PlacedNuclearResolver.INSTANCE.matches(state, entry)) return loot;

        Item expected = ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(entry.formId()));
        ItemStack preserved = entry.toStack();
        if (expected != null && !preserved.isEmpty()) {
            for (ItemStack stack : loot) {
                if (!stack.is(expected)) continue;
                stack.setTag(preserved.getTag() == null ? null : preserved.getTag().copy());
                break;
            }
        }
        data.remove(pos);
        NuclearSurfaceScanner.unmarkPlaced(context.getLevel(), pos);
        return loot;
    }

    @Override
    public Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier> codec() {
        return CODEC;
    }
}
