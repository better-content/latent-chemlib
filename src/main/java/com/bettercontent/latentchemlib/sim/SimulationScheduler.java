package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.data.LatentDataManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;

public class SimulationScheduler {
    public static final SimulationScheduler INSTANCE = new SimulationScheduler();

    private final SimulationBudgetLedger<ResourceKey<Level>> ledger = new SimulationBudgetLedger<>();

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !event.level.isClientSide() && event.level.getGameTime() % 20L == 0L) {
            ledger.reset(event.level.dimension());
        }
    }

    public boolean trySpend(Level level, SimulationBudget budget, int amount) {
        return ledger.trySpend(level.dimension(), budget, amount, LatentDataManager.INSTANCE.schedulerProfile());
    }

    public boolean trySpendAll(Level level, Map<SimulationBudget, Integer> amounts) {
        return ledger.trySpendAll(level.dimension(), amounts, LatentDataManager.INSTANCE.schedulerProfile());
    }
}
