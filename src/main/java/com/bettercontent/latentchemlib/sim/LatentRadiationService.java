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
        emit(level, pos, radiationLevel, 0.0);
    }

    public static void emit(ServerLevel level, BlockPos pos, double radiationStrength, double heatStrength) {
        MinecraftForge.EVENT_BUS.post(new RadiationEmissionEvent(level, pos, radiationStrength, heatStrength));
        LatentChemlibMod.LOGGER.debug("Nuclear fixed emission radiation={} heat={} at {}", radiationStrength, heatStrength, pos);
    }

    public static class RadiationEmissionEvent extends Event {
        private final ServerLevel level;
        private final BlockPos pos;
        private final double radiationStrength;
        private final double heatStrength;

        public RadiationEmissionEvent(ServerLevel level, BlockPos pos, int radiationLevel) {
            this(level, pos, radiationLevel, 0.0);
        }

        public RadiationEmissionEvent(ServerLevel level, BlockPos pos, double radiationLevel, double heatStrength) {
            this.level = level;
            this.pos = pos;
            this.radiationStrength = radiationLevel;
            this.heatStrength = heatStrength;
        }

        public ServerLevel level() {
            return level;
        }

        public BlockPos pos() {
            return pos;
        }

        public int radiationLevel() {
            return (int) Math.min(Integer.MAX_VALUE, Math.ceil(radiationStrength));
        }

        public double radiationStrength() { return radiationStrength; }
        public double heatStrength() { return heatStrength; }
    }
}
