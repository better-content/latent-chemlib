package com.gerald.latentchemlib.blockentity;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.data.LatentDataManager;
import com.gerald.latentchemlib.data.MachineProfile;
import com.gerald.latentchemlib.data.ReactionRule;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.EmergentMath;
import com.gerald.latentchemlib.sim.GasFluidCodec;
import com.gerald.latentchemlib.sim.GasFluidStorage;
import com.gerald.latentchemlib.sim.MachineTransfer;
import com.gerald.latentchemlib.sim.NuclearSimulationService;
import com.gerald.latentchemlib.sim.ReactionRuleSelector;
import com.gerald.latentchemlib.sim.SimulationBudget;
import com.gerald.latentchemlib.sim.SimulationScheduler;
import com.gerald.heatsync.api.HeatBlockEntity;
import com.gerald.heatsync.api.HeatCapabilities;
import com.gerald.heatsync.api.IHeatStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LatentMachineBlockEntity extends BlockEntity implements HeatBlockEntity {
    private ChemicalState stored = ChemicalState.empty();
    private float heat;
    private LazyOptional<IHeatStorage> heatCapability = LazyOptional.of(() -> this);
    private final IFluidHandler fluidHandler = new GasFluidStorage(
        () -> stored,
        this::setStoredState,
        () -> GasFluidCodec.millibucketsForMass(machineProfile().machineMassCapacity()),
        this::canFillGas,
        this::canDrainGas
    );
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> fluidHandler);

    public LatentMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(LatentChemlibMod.MACHINE_ENTITY.get(), pos, blockState);
    }

    public ChemicalState storedState() {
        return stored;
    }

    public void setStoredState(ChemicalState state) {
        stored = state;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = ChemicalState.load(tag.getCompound("stored"));
        heat = Math.min(configuredMaxHeat(), Math.max(0.0f, tag.getFloat("heat")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("stored", stored.save());
        tag.putFloat("heat", heat);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (!remove && cap == HeatCapabilities.INSTANCE.getHEAT()) {
            return heatCapability.cast();
        }
        if (!remove && cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        heatCapability.invalidate();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        heatCapability = LazyOptional.of(() -> this);
        fluidCapability = LazyOptional.of(() -> fluidHandler);
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, LatentMachineBlockEntity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % 20L != 0L) return;
        if (!SimulationScheduler.INSTANCE.trySpend(serverLevel, SimulationBudget.CLOUD_UPDATES, 1)) return;
        NuclearSimulationService.StateProcessResult nuclear = NuclearSimulationService.INSTANCE.processChemicalState(
            serverLevel,
            pos,
            entity.stored,
            1.0,
            entity,
            stack -> Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack)
        );
        if (nuclear.budgetExhausted()) return;
        if (nuclear.mutated()) {
            entity.stored = nuclear.state();
            entity.setChanged();
            return;
        }
        Block block = blockState.getBlock();
        if (block == LatentChemlibMod.GAS_CAPTURE.get()) {
            entity.capture(serverLevel);
        } else if (block == LatentChemlibMod.GAS_RELEASE.get()) {
            entity.release(serverLevel);
        } else if (block == LatentChemlibMod.GAS_REACTION_CHAMBER.get()) {
            entity.heat = Math.min(entity.heat, entity.configuredMaxHeat());
            entity.stored = EmergentMath.chamberAgitation(entity.stored, LatentDataManager.INSTANCE.machineProfile());
            entity.applyReactionRule(serverLevel);
        }
        entity.setChanged();
    }

    public InteractionResult useHeldCell(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(LatentChemlibMod.SEALED_CHEMICAL_CELL.get())) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel)) return InteractionResult.SUCCESS;
        return FluidUtil.interactWithFluidHandler(player, hand, fluidHandler)
            ? InteractionResult.CONSUME
            : InteractionResult.PASS;
    }

    private void capture(ServerLevel level) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (!(neighbor instanceof ChemicalCloudBlockEntity cloud)) continue;
            ChemicalState cloudState = cloud.chemicalState();
            if (stored.mass() > 0.0 && !stored.chemicalId().equals(cloudState.chemicalId())) continue;
            double amount = MachineTransfer.captureAmount(stored.mass(), cloudState.mass(), machineProfile().machineMassCapacity());
            if (amount <= 0.0) return;
            ChemicalState moved = cloud.extractMass(amount);
            stored = stored.merge(moved);
            return;
        }
    }

    private void release(ServerLevel level) {
        if (stored.mass() <= 0.0) return;
        BlockPos target = worldPosition.above();
        if (!level.getBlockState(target).isAir() && !level.getBlockState(target).canBeReplaced()) return;
        if (!(level.getBlockEntity(target) instanceof ChemicalCloudBlockEntity)) {
            level.setBlock(target, LatentChemlibMod.CHEMICAL_CLOUD.get().defaultBlockState(), 3);
        }
        if (level.getBlockEntity(target) instanceof ChemicalCloudBlockEntity cloud) {
            ChemicalState moved = stored.withMass(Math.min(MachineTransfer.TRANSFER_MASS, stored.mass()));
            cloud.seed(moved);
            stored = stored.withMass(stored.mass() - moved.mass());
        }
    }

    private void applyReactionRule(ServerLevel level) {
        if (stored.mass() <= 0.0) return;
        var selected = ReactionRuleSelector.firstMatch(LatentDataManager.INSTANCE.reactionRules(), stored, heat);
        if (selected.isPresent()) {
            ReactionRule rule = selected.get();
            heat = Math.min(configuredMaxHeat(), Math.max(0.0f, heat - rule.heatCost() + rule.heatEmission()));
            stored = rule.apply(stored);
            var item = rule.outputItemValue();
            if (item != null) {
                BlockPos target = worldPosition.above();
                Containers.dropItemStack(level, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, new ItemStack(item));
            }
        }
    }

    @Override
    public float getHeat() {
        return heat;
    }

    @Override
    public float getMaxHeat() {
        return configuredMaxHeat();
    }

    @Override
    public float getThermalCapacity() {
        return configuredMaxHeat();
    }

    @Override
    public float getThermalResistance() {
        return 1.0f;
    }

    @Override
    public boolean canConnect(Direction side) {
        return true;
    }

    @Override
    public boolean canAdd(Direction side) {
        return true;
    }

    @Override
    public boolean canExtract(Direction side) {
        return true;
    }

    @Override
    public float addHeat(float amount, boolean simulate) {
        if (amount <= 0.0f) return 0.0f;
        float accepted = Math.min(amount, Math.max(0.0f, configuredMaxHeat() - heat));
        if (!simulate && accepted > 0.0f) addHeat(accepted);
        return accepted;
    }

    @Override
    public float extractHeat(float amount, boolean simulate) {
        if (amount <= 0.0f) return 0.0f;
        float extracted = Math.min(amount, Math.max(0.0f, heat));
        if (!simulate && extracted > 0.0f) setHeat(heat - extracted);
        return extracted;
    }

    @Override
    public void addHeat(float heat) {
        this.heat = Math.min(configuredMaxHeat(), Math.max(0.0f, this.heat + heat));
    }

    @Override
    public void setHeat(float heat) {
        this.heat = Math.min(configuredMaxHeat(), Math.max(0.0f, heat));
    }

    @Override
    public float maxHeat() {
        return configuredMaxHeat();
    }

    private MachineProfile machineProfile() {
        return LatentDataManager.INSTANCE.machineProfile();
    }

    private float configuredMaxHeat() {
        MachineProfile profile = machineProfile();
        return getBlockState().getBlock() == LatentChemlibMod.GAS_REACTION_CHAMBER.get()
            ? profile.reactionChamberMaxHeat()
            : profile.defaultMaxHeat();
    }

    private boolean canFillGas() {
        Block block = getBlockState().getBlock();
        return block == LatentChemlibMod.GAS_TANK.get()
            || block == LatentChemlibMod.GAS_REACTION_CHAMBER.get()
            || block == LatentChemlibMod.GAS_RELEASE.get();
    }

    private boolean canDrainGas() {
        Block block = getBlockState().getBlock();
        return block == LatentChemlibMod.GAS_CAPTURE.get()
            || block == LatentChemlibMod.GAS_TANK.get()
            || block == LatentChemlibMod.GAS_REACTION_CHAMBER.get();
    }
}
