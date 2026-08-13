package com.bettercontent.latentchemlib.integration.adpother;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class DelayedEmissionContextStore {
    public static final DelayedEmissionContextStore INSTANCE = new DelayedEmissionContextStore();

    private final Map<BlockEntity, Map<Pollutant<?>, Accumulator>> contexts = new WeakHashMap<>();

    private DelayedEmissionContextStore() {}

    public synchronized void record(
        BlockEntity blockEntity,
        Pollutant<?> pollutant,
        int units,
        AdpotherEmissionContext context
    ) {
        if (blockEntity == null || pollutant == null || units <= 0 || context == null) return;
        contexts.computeIfAbsent(blockEntity, ignored -> new HashMap<>())
            .computeIfAbsent(pollutant, ignored -> new Accumulator())
            .add(units, context);
    }

    public synchronized Optional<AdpotherEmissionContext> contextFor(BlockEntity blockEntity, Pollutant<?> pollutant) {
        Map<Pollutant<?>, Accumulator> byPollutant = contexts.get(blockEntity);
        if (byPollutant == null) return Optional.empty();
        Accumulator accumulator = byPollutant.get(pollutant);
        return accumulator == null ? Optional.empty() : accumulator.context();
    }

    public synchronized void consume(BlockEntity blockEntity, Pollutant<?> pollutant, int units) {
        if (units <= 0) return;
        Map<Pollutant<?>, Accumulator> byPollutant = contexts.get(blockEntity);
        if (byPollutant == null) return;
        Accumulator accumulator = byPollutant.get(pollutant);
        if (accumulator == null) return;
        accumulator.consume(units);
        if (accumulator.units <= 0) byPollutant.remove(pollutant);
        if (byPollutant.isEmpty()) contexts.remove(blockEntity);
    }

    static final class Accumulator {
        private int units;
        private double temperatureUnits;
        private double chargeUnits;
        private double energyUnits;

        void add(int addedUnits, AdpotherEmissionContext context) {
            units += addedUnits;
            temperatureUnits += context.temperature() * addedUnits;
            chargeUnits += context.charge() * addedUnits;
            energyUnits += context.energyPerUnit() * addedUnits;
        }

        Optional<AdpotherEmissionContext> context() {
            if (units <= 0) return Optional.empty();
            return Optional.of(new AdpotherEmissionContext(
                temperatureUnits / units,
                chargeUnits / units,
                energyUnits / units
            ));
        }

        void consume(int consumedUnits) {
            int bounded = Math.min(Math.max(0, consumedUnits), units);
            if (bounded == 0) return;
            double ratio = (double) (units - bounded) / units;
            units -= bounded;
            temperatureUnits *= ratio;
            chargeUnits *= ratio;
            energyUnits *= ratio;
        }
    }
}
