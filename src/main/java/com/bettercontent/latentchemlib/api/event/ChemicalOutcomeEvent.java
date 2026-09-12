package com.bettercontent.latentchemlib.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;

/** Published only after matter replacement, disturbed placement or accepted thermal transfer. */
public final class ChemicalOutcomeEvent extends Event {
    public enum Kind { GAS_RELEASED, RADIOACTIVE_ACTIVATED, HEAT_ACCEPTED }
    private final ServerLevel level;
    private final BlockPos pos;
    private final Kind kind;
    private final UUID actor;
    private final BlockEntity device;
    private final double amount;
    private ChemicalOutcomeEvent(ServerLevel level, BlockPos pos, Kind kind, UUID actor, BlockEntity device, double amount) {
        this.level = level; this.pos = pos.immutable(); this.kind = kind;
        this.actor = actor; this.device = device; this.amount = amount;
    }
    public static void publish(ServerLevel level, BlockPos pos, Kind kind, Object holder, double amount) {
        if (level == null || pos == null || !Double.isFinite(amount) || amount <= 0) return;
        Entity entity = holder instanceof Inventory inventory ? inventory.player : holder instanceof Entity e ? e : null;
        if (entity instanceof ItemEntity item) entity = item.getOwner();
        UUID actor = entity instanceof Player ? entity.getUUID() : null;
        MinecraftForge.EVENT_BUS.post(new ChemicalOutcomeEvent(level, pos, kind, actor,
            holder instanceof BlockEntity block ? block : null, amount));
    }
    public ServerLevel level() { return level; }
    public BlockPos pos() { return pos; }
    public Kind kind() { return kind; }
    public UUID actor() { return actor; }
    public BlockEntity device() { return device; }
    public double amount() { return amount; }
}
