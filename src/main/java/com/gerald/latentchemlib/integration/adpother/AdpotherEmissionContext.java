package com.gerald.latentchemlib.integration.adpother;

import com.endertech.minecraft.mods.adpother.sources.Emitter;
import com.endertech.minecraft.mods.adpother.sources.Fuel;
import com.endertech.minecraft.mods.adpother.sources.SourceBase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntSupplier;

public record AdpotherEmissionContext(double temperature, double charge, double energyPerUnit) {
    public static final AdpotherEmissionContext AMBIENT = new AdpotherEmissionContext(293.0, 0.0, 6.0);
    private static final ThreadLocal<Deque<AdpotherEmissionContext>> ACTIVE =
        ThreadLocal.withInitial(ArrayDeque::new);

    public static AdpotherEmissionContext forEmitter(Emitter emitter, Fuel fuel) {
        String emitterId = emitter == null ? "" : emitter.getRelatedId().toString();
        String fuelId = fuel == null ? "" : fuel.getRelatedId().toString();
        return classify(emitterId + " " + fuelId);
    }

    public static AdpotherEmissionContext forSource(SourceBase source) {
        return classify(source == null ? "" : source.getRelatedId().toString());
    }

    static AdpotherEmissionContext classify(String rawId) {
        String id = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (containsAny(id, "nether_star", "dragon_breath", "ender_", "ender:", "plasma", "electric_arc")) {
            return new AdpotherEmissionContext(3000.0, 0.25, 250.0);
        }
        if (containsAny(id, "tnt", "gunpowder", "explosion", "firework")) {
            return new AdpotherEmissionContext(2200.0, 0.10, 120.0);
        }
        if (containsAny(id, "blaze", "soul_", "magma", "nether")) {
            return new AdpotherEmissionContext(1800.0, 0.05, 60.0);
        }
        if (containsAny(id, "blast", "smelter", "smeltery", "kiln", "foundry", "coke_oven")) {
            return new AdpotherEmissionContext(1400.0, 0.01, 36.0);
        }
        if (containsAny(id, "furnace", "engine", "generator", "turbine", "boiler", "burner", "stove")) {
            return new AdpotherEmissionContext(900.0, 0.0, 20.0);
        }
        if (containsAny(id, "torch", "campfire", "fire", "lava")) {
            return new AdpotherEmissionContext(700.0, 0.0, 12.0);
        }
        if (containsAny(id, "smolder", "peat", "charcoal_pit")) {
            return new AdpotherEmissionContext(450.0, 0.0, 8.0);
        }
        return AMBIENT;
    }

    public static Optional<AdpotherEmissionContext> current() {
        return Optional.ofNullable(ACTIVE.get().peek());
    }

    public static void push(AdpotherEmissionContext context) {
        ACTIVE.get().push(context == null ? AMBIENT : context);
    }

    public static void pop() {
        Deque<AdpotherEmissionContext> contexts = ACTIVE.get();
        if (!contexts.isEmpty()) contexts.pop();
        if (contexts.isEmpty()) ACTIVE.remove();
    }

    public static int callWith(AdpotherEmissionContext context, IntSupplier action) {
        push(context);
        try {
            return action.getAsInt();
        } finally {
            pop();
        }
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) return true;
        }
        return false;
    }
}
