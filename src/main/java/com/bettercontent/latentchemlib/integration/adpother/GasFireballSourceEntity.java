package com.bettercontent.latentchemlib.integration.adpother;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Internal explosion attribution only. This entity is never spawned or persisted. */
public final class GasFireballSourceEntity extends Entity {
    public GasFireballSourceEntity(EntityType<? extends GasFireballSourceEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
