package com.bettercontent.latentchemlib.blockentity;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import com.bettercontent.latentchemlib.block.ChemicalCloudBlock;
import com.bettercontent.latentchemlib.integration.adpother.AdpotherAtmosphereBridge;
import com.bettercontent.latentchemlib.sim.AtmosphericPollutantState;
import com.bettercontent.latentchemlib.sim.ChemicalState;
import com.bettercontent.latentchemlib.sim.CloudInsertionService;
import com.bettercontent.latentchemlib.sim.ChemicalCloudVisuals;
import com.bettercontent.latentchemlib.sim.SimulationBudget;
import com.bettercontent.latentchemlib.sim.SimulationScheduler;
import com.endertech.minecraft.mods.adpother.blocks.AbstractGas;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.forge.world.BiomeId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A bounded, single-pollutant atmospheric cell. Complex chemistry stays in containers. */
public class ChemicalCloudBlockEntity extends BlockEntity {
    private AtmosphericPollutantState pollutantState = AtmosphericPollutantState.EMPTY;

    public ChemicalCloudBlockEntity(BlockPos pos, BlockState blockState) {
        super(LatentChemlibMod.CHEMICAL_CLOUD_ENTITY.get(), pos, blockState);
    }

    public AtmosphericPollutantState pollutantState() {
        return pollutantState;
    }

    public int capacity() {
        return AdpotherAtmosphereBridge.INSTANCE.pollutantById(pollutantState.pollutantId())
            .map(pollutant -> Math.max(1, pollutant.getPollutionCapacity()))
            .orElse(0);
    }

    public int insertUnits(String pollutantId, int requested) {
        int amount = Math.max(0, requested);
        if (amount == 0 || pollutantId == null || pollutantId.isBlank()) return 0;
        if (!pollutantState.isEmpty() && !pollutantState.pollutantId().equals(pollutantId)) return 0;
        int capacity = AdpotherAtmosphereBridge.INSTANCE.pollutantById(pollutantId)
            .map(pollutant -> Math.max(1, pollutant.getPollutionCapacity()))
            .orElse(0);
        int accepted = Math.min(amount, Math.max(0, capacity - pollutantState.units()));
        if (accepted <= 0) return 0;
        pollutantState = new AtmosphericPollutantState(pollutantId, pollutantState.units() + accepted);
        syncVisualState();
        setChanged();
        return accepted;
    }

    public int extractUnits(String pollutantId, int requested) {
        if (!pollutantState.pollutantId().equals(pollutantId)) return 0;
        int extracted = Math.min(Math.max(0, requested), pollutantState.units());
        if (extracted <= 0) return 0;
        pollutantState = new AtmosphericPollutantState(pollutantId, pollutantState.units() - extracted);
        syncVisualState();
        setChanged();
        return extracted;
    }

    /** Compatibility boundary for contained systems; atmospheric state itself is never a mixture. */
    public ChemicalState chemicalState() {
        if (pollutantState.isEmpty()) return ChemicalState.empty();
        return AdpotherAtmosphereBridge.INSTANCE.pollutantById(pollutantState.pollutantId())
            .map(pollutant -> new ChemicalState(
                AdpotherAtmosphereBridge.INSTANCE.chemicalId(pollutant),
                pollutantState.units() * CloudInsertionService.MASS_PER_ADPOTHER_UNIT,
                pollutantState.units(), 293.0, 0.0, 0.0
            ))
            .orElse(ChemicalState.empty());
    }

    public void seed(ChemicalState incoming) {
        if (incoming == null || incoming.mass() <= 0.0 || !incoming.isPure()) return;
        AdpotherAtmosphereBridge.INSTANCE.pollutantFor(incoming.chemicalId()).ifPresent(pollutant ->
            insertUnits(AdpotherAtmosphereBridge.INSTANCE.pollutantId(pollutant),
                (int) Math.floor(incoming.mass() / CloudInsertionService.MASS_PER_ADPOTHER_UNIT))
        );
    }

    public ChemicalState extractMass(double mass) {
        if (mass <= 0.0 || pollutantState.isEmpty()) return ChemicalState.empty();
        int requested = (int) Math.floor(mass / CloudInsertionService.MASS_PER_ADPOTHER_UNIT);
        String chemicalId = chemicalState().chemicalId();
        int extracted = extractUnits(pollutantState.pollutantId(), requested);
        return extracted <= 0 ? ChemicalState.empty() : new ChemicalState(
            chemicalId, extracted * CloudInsertionService.MASS_PER_ADPOTHER_UNIT,
            extracted, 293.0, 0.0, 0.0
        );
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pollutantState = AtmosphericPollutantState.load(tag.getCompound("atmospheric_pollutant"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("atmospheric_pollutant", pollutantState.save());
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, ChemicalCloudBlockEntity entity) {
        if (level.isClientSide) {
            int tier = blockState.getValue(ChemicalCloudBlock.DIFFUSION);
            int particleInterval = switch (tier) {
                case 0 -> 2;
                case 1 -> 3;
                case 2 -> 4;
                default -> 6;
            };
            if (!entity.pollutantState.isEmpty() && level.random.nextInt(particleInterval) == 0) {
                int color = ChemicalCloudVisuals.colorForPollutant(entity.pollutantState.pollutantId());
                level.addParticle(net.minecraft.core.particles.ParticleTypes.AMBIENT_ENTITY_EFFECT,
                    pos.getX() + level.random.nextDouble(), pos.getY() + level.random.nextDouble(), pos.getZ() + level.random.nextDouble(),
                    ((color >> 16) & 0xFF) / 255.0, ((color >> 8) & 0xFF) / 255.0, (color & 0xFF) / 255.0);
            }
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (entity.pollutantState.isEmpty()) {
            level.removeBlock(pos, false);
            return;
        }
        if (serverLevel.getGameTime() % 20L != 0L
            || !SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationBudget.CLOUD_UPDATES, 1)) return;
        AdpotherAtmosphereBridge.INSTANCE.pollutantById(entity.pollutantState.pollutantId()).ifPresentOrElse(pollutant -> {
            BlockState nativeState = entity.nativePollutantState(pollutant);
            if (serverLevel.getRandom().nextInt(20) == 0
                && SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationBudget.NEIGHBOR_OPS, 1)) {
                if (pollutant instanceof AbstractGas gas) {
                    gas.tryAffectBlocksBelow(nativeState, serverLevel, pos, BiomeId.from(serverLevel, pos));
                } else {
                    pollutant.tryAffectBlockAt(serverLevel, pos.below(), java.util.Optional.of(net.minecraft.core.Direction.UP),
                        com.endertech.minecraft.mods.adpother.impacts.AbstractPollutionImpacts.ImpactType.CONTACT, nativeState);
                }
            }
            if (serverLevel.getGameTime() % 100L == 0L) entity.moveOneUnit(serverLevel, pollutant);
            if (pollutant instanceof AbstractGas gas && gas.shouldDissipateExcessive(serverLevel, pos, BiomeId.from(serverLevel, pos))
                && serverLevel.getRandom().nextInt(20) == 0) entity.extractUnits(entity.pollutantState.pollutantId(), 1);
        }, () -> level.removeBlock(pos, false));
        if (entity.pollutantState.isEmpty() && level.getBlockEntity(pos) == entity) level.removeBlock(pos, false);
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) load(packet.getTag());
    }

    private void syncVisualState() {
        if (level == null || level.isClientSide) return;
        BlockState current = getBlockState();
        if (!current.hasProperty(ChemicalCloudBlock.DIFFUSION)) return;
        int tier = ChemicalCloudVisuals.diffusionTier(pollutantState.units(), capacity());
        if (current.getValue(ChemicalCloudBlock.DIFFUSION) != tier) {
            level.setBlock(worldPosition, current.setValue(ChemicalCloudBlock.DIFFUSION, tier), 3);
        } else {
            level.sendBlockUpdated(worldPosition, current, current, 3);
        }
    }

    private BlockState nativePollutantState(Pollutant<?> pollutant) {
        int capacity = Math.max(1, pollutant.getPollutionCapacity());
        double fraction = pollutantState.units() / (double) capacity;
        Pollutant.Density density = fraction > 2.0 / 3.0 ? Pollutant.Density.HEAVY
            : fraction > 1.0 / 3.0 ? Pollutant.Density.MEDIUM : Pollutant.Density.LIGHT;
        BlockState state = pollutant.defaultBlockState();
        return state.hasProperty(Pollutant.DENSITY) ? state.setValue(Pollutant.DENSITY, density) : state;
    }

    private void moveOneUnit(ServerLevel level, Pollutant<?> pollutant) {
        if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NEIGHBOR_OPS, 1)) return;
        var direction = pollutant.getMotionFacing(level, worldPosition, BiomeId.from(level, worldPosition));
        if (direction.isEmpty()) return;
        BlockPos target = worldPosition.relative(direction.get());
        int inserted = CloudInsertionService.INSTANCE.insertAt(level, target, pollutant, 1);
        if (inserted > 0) extractUnits(pollutantState.pollutantId(), inserted);
    }
}
