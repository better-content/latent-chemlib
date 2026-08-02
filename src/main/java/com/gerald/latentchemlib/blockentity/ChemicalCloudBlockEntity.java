package com.gerald.latentchemlib.blockentity;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.block.ChemicalCloudBlock;
import com.gerald.latentchemlib.data.ChemicalTraits;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.EmergentMath;
import com.gerald.latentchemlib.sim.NuclearSimulationService;
import com.gerald.latentchemlib.sim.SimulationBudget;
import com.gerald.latentchemlib.sim.SimulationScheduler;
import com.gerald.latentchemlib.integration.adpother.AdpotherCloudView;
import com.gerald.latentchemlib.integration.adpother.LatentGasHazardService;
import com.endertech.minecraft.forge.world.BiomeId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChemicalCloudBlockEntity extends BlockEntity {
    private ChemicalState state = ChemicalState.empty();
    private int age;

    public ChemicalCloudBlockEntity(BlockPos pos, BlockState blockState) {
        super(LatentChemlibMod.CHEMICAL_CLOUD_ENTITY.get(), pos, blockState);
    }

    public ChemicalState chemicalState() {
        return state;
    }

    public void seed(ChemicalState incoming) {
        if (state.mass() <= 0.0 || state.chemicalId().equals(incoming.chemicalId())) {
            state = state.merge(incoming);
            syncVisualState();
            setChanged();
        }
    }

    public ChemicalState extractMass(double mass) {
        double moved = Math.min(Math.max(0.0, mass), state.mass());
        if (moved <= 0.0) return ChemicalState.empty();
        ChemicalState extracted = state.withMass(moved);
        state = state.withMass(state.mass() - moved);
        syncVisualState();
        setChanged();
        return extracted;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        state = ChemicalState.load(tag.getCompound("chemical_state"));
        age = tag.getInt("age");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("chemical_state", state.save());
        tag.putInt("age", age);
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, ChemicalCloudBlockEntity entity) {
        if (level.isClientSide) {
            if (level.random.nextInt(4) == 0 && entity.state.mass() > 0.0) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.AMBIENT_ENTITY_EFFECT,
                    pos.getX() + level.random.nextDouble(), pos.getY() + level.random.nextDouble(), pos.getZ() + level.random.nextDouble(),
                    0.72, 0.86, 0.92);
            }
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) return;
        int cadence = EmergentMath.updateCadence(entity.state);
        if (cadence <= 0 || serverLevel.getGameTime() % cadence != 0L) return;
        if (!SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationBudget.CLOUD_UPDATES, 1)) return;

        NuclearSimulationService.StateProcessResult nuclear = NuclearSimulationService.INSTANCE.processChemicalState(
            serverLevel,
            pos,
            entity.state,
            cadence / 20.0,
            null,
            stack -> Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack)
        );
        if (nuclear.budgetExhausted()) return;
        if (nuclear.mutated()) {
            entity.state = nuclear.state();
            entity.syncVisualState();
            entity.setChanged();
            return;
        }

        if (LatentGasHazardService.INSTANCE.tryIgnite(serverLevel, pos)) return;
        if (serverLevel.getRandom().nextInt(20) == 0
            && SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationBudget.NEIGHBOR_OPS, 1)) {
            AdpotherCloudView.INSTANCE.gasSelectorAt(serverLevel, pos).ifPresent(selector ->
                selector.tryAffectBlocksBelow(
                    selector.defaultBlockState(),
                    serverLevel,
                    pos,
                    BiomeId.from(serverLevel, pos)
                )
            );
            if (!(serverLevel.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity)) return;
        }

        ChemicalTraits traits = LatentDataManager.INSTANCE.traits(entity.state.chemicalId());
        entity.diffuse(serverLevel, traits);
        entity.erode(serverLevel, traits);
        entity.state = EmergentMath.coolAndSettle(entity.state, traits);
        entity.age++;
        if (EmergentMath.shouldDissipate(entity.state, traits)) {
            level.removeBlock(pos, false);
            return;
        }
        entity.syncVisualState();
        entity.setChanged();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) load(tag);
    }

    private void syncVisualState() {
        if (level == null || level.isClientSide) return;
        BlockState current = getBlockState();
        if (!current.hasProperty(ChemicalCloudBlock.DIFFUSION)) return;
        int nextTier = ChemicalCloudBlock.diffusionTier(state);
        if (current.getValue(ChemicalCloudBlock.DIFFUSION) != nextTier) {
            level.setBlock(worldPosition, current.setValue(ChemicalCloudBlock.DIFFUSION, nextTier), 3);
        } else {
            level.sendBlockUpdated(worldPosition, current, current, 3);
        }
    }

    private void diffuse(ServerLevel level, ChemicalTraits traits) {
        double movable = EmergentMath.diffusionMass(state, traits);
        if (movable <= 0.0) return;
        List<DiffusionTarget> candidates = new ArrayList<>(Direction.values().length);
        List<Direction> directions = new ArrayList<>(List.of(Direction.values()));
        Collections.shuffle(directions, new java.util.Random(level.getRandom().nextLong()));
        for (Direction direction : directions) {
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NEIGHBOR_OPS, 1)) return;
            BlockPos target = worldPosition.relative(direction);
            if (!level.isInWorldBounds(target)) continue;
            BlockState targetState = level.getBlockState(target);
            ChemicalCloudBlockEntity targetEntity = level.getBlockEntity(target) instanceof ChemicalCloudBlockEntity cloud ? cloud : null;
            double targetDensity = targetEntity == null ? 0.0 : targetEntity.state.density();
            double pressure = EmergentMath.pressureGradient(state, targetDensity, traits);
            if (pressure <= 0.0) continue;
            double weight = EmergentMath.directionalDiffusionWeight(state, direction, pressure);
            if (weight <= 0.0) continue;
            candidates.add(new DiffusionTarget(target, targetState, targetEntity, pressure, weight));
        }
        if (candidates.isEmpty()) return;

        double totalWeight = candidates.stream().mapToDouble(DiffusionTarget::weight).sum();
        if (totalWeight <= 0.0) return;

        double remaining = movable;
        for (int i = 0; i < candidates.size() && remaining > 0.0; i++) {
            DiffusionTarget candidate = candidates.get(i);
            ChemicalCloudBlockEntity targetEntity = candidate.resolve(level);
            if (targetEntity == null) continue;
            double share = i == candidates.size() - 1 ? remaining : movable * (candidate.weight() / totalWeight);
            double moved = Math.min(remaining, Math.min(candidate.pressure(), share));
            if (moved <= 0.0) continue;
            targetEntity.seed(extractMass(moved));
            remaining -= moved;
        }
    }

    private void erode(ServerLevel level, ChemicalTraits traits) {
        double flux = EmergentMath.heatFlux(state, traits);
        if (flux <= 0.0) return;
        for (Direction direction : Direction.values()) {
            if (!SimulationScheduler.INSTANCE.trySpend(level, SimulationBudget.NEIGHBOR_OPS, 1)) return;
            BlockPos target = worldPosition.relative(direction);
            BlockState targetState = level.getBlockState(target);
            if (targetState.isAir() || targetState.is(Blocks.BEDROCK)) continue;
            double resistance = Math.max(0.1, targetState.getBlock().defaultDestroyTime());
            if (EmergentMath.erodes(flux, resistance)) {
                level.destroyBlock(target, false);
                state = state.withEnergy(Math.max(0.0, state.energy() - resistance * 140.0));
                return;
            }
        }
    }

    private record DiffusionTarget(
        BlockPos target,
        BlockState targetState,
        ChemicalCloudBlockEntity targetEntity,
        double pressure,
        double weight
    ) {
        private ChemicalCloudBlockEntity resolve(ServerLevel level) {
            if (targetEntity != null) return targetEntity;
            if (!targetState.isAir() && !targetState.canBeReplaced()) return null;
            level.setBlock(target, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3);
            return level.getBlockEntity(target) instanceof ChemicalCloudBlockEntity cloud ? cloud : null;
        }
    }
}
