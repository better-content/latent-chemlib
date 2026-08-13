package com.bettercontent.latentchemlib.client;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import com.bettercontent.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.bettercontent.latentchemlib.sim.ChemicalState;
import com.smashingmods.chemlib.api.Chemical;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = LatentChemlibMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ChemicalCloudClient {
    private static final int FALLBACK_COLOR = 0xD8F4FF;
    private static final Map<String, Integer> COLOR_CACHE = new HashMap<>();

    private ChemicalCloudClient() {}

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0 || level == null || pos == null) return 0xFFFFFF;
            if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) return FALLBACK_COLOR;
            return colorFor(cloud.chemicalState());
        }, LatentChemlibMod.CHEMICAL_CLOUD.get());
    }

    private static int colorFor(ChemicalState state) {
        if (state.mass() <= 0.0 || state.chemicalId().isBlank() || state.chemicalId().equals("minecraft:air")) {
            return FALLBACK_COLOR;
        }
        return COLOR_CACHE.computeIfAbsent(state.chemicalId(), ChemicalCloudClient::resolveColor);
    }

    private static int resolveColor(String chemicalId) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(chemicalId));
        if (item instanceof Chemical chemical) {
            return saturate(chemical.getColor());
        }
        return FALLBACK_COLOR;
    }

    private static int saturate(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (delta <= 0.001f) return color & 0xFFFFFF;

        float hue;
        if (max == r) {
            hue = ((g - b) / delta) % 6.0f;
        } else if (max == g) {
            hue = ((b - r) / delta) + 2.0f;
        } else {
            hue = ((r - g) / delta) + 4.0f;
        }
        hue /= 6.0f;
        if (hue < 0.0f) hue += 1.0f;

        float saturation = Math.min(1.0f, Math.max(0.42f, delta / max) * 1.35f);
        float value = Math.min(1.0f, Math.max(0.28f, max) * 1.12f);
        return hsvToRgb(hue, saturation, value);
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float h = hue * 6.0f;
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - saturation * fraction);
        float t = value * (1.0f - saturation * (1.0f - fraction));

        return switch (sector % 6) {
            case 0 -> rgb(value, t, p);
            case 1 -> rgb(q, value, p);
            case 2 -> rgb(p, value, t);
            case 3 -> rgb(p, q, value);
            case 4 -> rgb(t, p, value);
            default -> rgb(value, p, q);
        };
    }

    private static int rgb(float r, float g, float b) {
        int ri = Math.max(0, Math.min(255, Math.round(r * 255.0f)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255.0f)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255.0f)));
        return (ri << 16) | (gi << 8) | bi;
    }
}
