package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.integration.adpother.AdpotherAtmosphereBridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChemicalCloudVisuals {
    public static final int FALLBACK_COLOR = 0xD8F4FF;
    private static final Map<String, Integer> COLOR_CACHE = new ConcurrentHashMap<>();

    private ChemicalCloudVisuals() {}

    public static int diffusionTier(ChemicalState state) {
        if (state.mass() <= 0.0 || state.density() < 0.25) return 3;
        if (state.density() < 1.0) return 2;
        if (state.density() < 3.0) return 1;
        return 0;
    }

    public static int diffusionTier(int units, int capacity) {
        if (units <= 0 || capacity <= 0) return 3;
        int occupiedBand = Math.min(3, Math.max(0, (int) Math.ceil(units * 4.0 / capacity) - 1));
        return 3 - occupiedBand;
    }

    public static int colorForPollutant(String pollutantId) {
        if (pollutantId == null || pollutantId.isBlank()) return FALLBACK_COLOR;
        return COLOR_CACHE.computeIfAbsent(pollutantId, ChemicalCloudVisuals::resolvePollutantColor);
    }

    private static int resolvePollutantColor(String pollutantId) {
        return AdpotherAtmosphereBridge.INSTANCE.pollutantById(pollutantId)
            .map(pollutant -> saturate(pollutant.getColor().getARGB()))
            .orElse(FALLBACK_COLOR);
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
