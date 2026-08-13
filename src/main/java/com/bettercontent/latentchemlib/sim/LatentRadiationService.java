package com.bettercontent.latentchemlib.sim;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.common.MinecraftForge;

public final class LatentRadiationService {
    private LatentRadiationService() {
    }

    public static void emit(ServerLevel level, BlockPos pos, int radiationLevel) {
        MinecraftForge.EVENT_BUS.post(new RadiationEmissionEvent(level, pos, radiationLevel));
        LatentChemlibMod.LOGGER.debug("Nuclear radiation emission level {} at {}", radiationLevel, pos);
    }

    public static class RadiationEmissionEvent extends Event {
        private final ServerLevel level;
        private final BlockPos pos;
        private final int radiationLevel;

        public RadiationEmissionEvent(ServerLevel level, BlockPos pos, int radiationLevel) {
            this.level = level;
            this.pos = pos;
            this.radiationLevel = radiationLevel;
        }

        public ServerLevel level() {
            return level;
        }

        public BlockPos pos() {
            return pos;
        }

        public int radiationLevel() {
            return radiationLevel;
        }
    }
}
