package com.bettercontent.latentchemlib.blockentity;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import com.bettercontent.latentchemlib.api.IChemicalStateHandler;
import com.bettercontent.latentchemlib.api.LatentCapabilities;
import com.bettercontent.latentchemlib.data.LatentDataManager;
import com.bettercontent.latentchemlib.data.MachineProfile;
import com.bettercontent.latentchemlib.data.ReactionRule;
import com.bettercontent.latentchemlib.sim.ChemicalState;
import com.bettercontent.latentchemlib.sim.EmergentMath;
import com.bettercontent.latentchemlib.sim.GasFluidCodec;
import com.bettercontent.latentchemlib.sim.GasFluidStorage;
import com.bettercontent.latentchemlib.sim.HeatReceiver;
import com.bettercontent.latentchemlib.sim.MachineTransfer;
import com.bettercontent.latentchemlib.sim.NuclearSimulationService;
import com.bettercontent.latentchemlib.sim.ReactionRuleSelector;
import com.bettercontent.latentchemlib.sim.SimulationBudget;
import com.bettercontent.latentchemlib.sim.SimulationScheduler;
import com.bettercontent.latentchemlib.integration.pneumatic.DryAirSeparation;
import com.bettercontent.latentchemlib.integration.pneumatic.PneumaticChemistryMode;
import com.bettercontent.latentchemlib.item.ChemicalCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.PneumaticRegistry;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LatentMachineBlockEntity extends BlockEntity implements HeatReceiver, IChemicalStateHandler {
    private static final int PNEUMATIC_AIR_VOLUME = 1_000;
    private static final double CHEMICAL_TRANSFER_MASS = 64.0;
    private static final List<Direction> ALL_DIRECTIONS = List.of(Direction.values());

    private ChemicalState stored = ChemicalState.empty();
    private float heat;
    private PneumaticChemistryMode transportMode = PneumaticChemistryMode.AIR;
    private int chemicalDirectionCursor;
    private final IAirHandlerMachine pneumaticAirHandler = PneumaticRegistry.getInstance()
        .getAirHandlerMachineFactory()
        .createTierOneAirHandler(PNEUMATIC_AIR_VOLUME);
    private LazyOptional<IChemicalStateHandler> chemicalCapability = LazyOptional.of(() -> this);
    private LazyOptional<IAirHandlerMachine> pneumaticAirCapability = LazyOptional.of(() -> pneumaticAirHandler);
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
        transportMode = tag.contains("transport_mode")
            ? PneumaticChemistryMode.load(tag.getString("transport_mode"))
            : PneumaticChemistryMode.AIR;
        if (tag.contains("pneumatic_air", CompoundTag.TAG_COMPOUND)) {
            pneumaticAirHandler.deserializeNBT(tag.getCompound("pneumatic_air"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("stored", stored.save());
        tag.putFloat("heat", heat);
        tag.putString("transport_mode", transportMode.name());
        tag.put("pneumatic_air", pneumaticAirHandler.serializeNBT());
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // Forge gathers attached capabilities from BlockEntity's super-constructor,
        // before this class's lazy fields have been initialized.
        if (!remove && chemicalCapability != null && cap == LatentCapabilities.CHEMICAL_STATE && exposesChemicalState()) {
            return chemicalCapability.cast();
        }
        if (!remove && pneumaticAirCapability != null && cap == PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY && exposesNativeAir()) {
            return pneumaticAirCapability.cast();
        }
        if (!remove && fluidCapability != null && cap == ForgeCapabilities.FLUID_HANDLER && exposesChemicalState()) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        chemicalCapability.invalidate();
        pneumaticAirCapability.invalidate();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        chemicalCapability = LazyOptional.of(() -> this);
        pneumaticAirCapability = LazyOptional.of(() -> pneumaticAirHandler);
        fluidCapability = LazyOptional.of(() -> fluidHandler);
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, LatentMachineBlockEntity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        entity.tickNativeAir();
        if (entity.isPneumaticChemicalTube() && entity.transportMode == PneumaticChemistryMode.CHEMICAL
            && serverLevel.getGameTime() % 5L == 0L) {
            entity.balanceChemicalNeighbor(serverLevel);
        }
        if (serverLevel.getGameTime() % 20L != 0L) return;
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
        } else if (block == LatentChemlibMod.DRY_AIR_SEPARATOR.get()) {
            entity.separateDryAir();
        }
        entity.setChanged();
    }

    public InteractionResult useHeldCell(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(LatentChemlibMod.SEALED_CHEMICAL_CELL.get())) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel)) return InteractionResult.SUCCESS;
        double cellCapacity = GasFluidCodec.massForMillibuckets(ChemicalCellItem.FLUID_CAPACITY);
        ChemicalState cell = ChemicalCellItem.state(stack);
        if (cell.mass() <= 0.0 && canDrainGas()) {
            ChemicalState extracted = extractChemical(Math.min(cellCapacity, stored.mass()), false);
            if (extracted.mass() > 0.0) {
                ChemicalCellItem.setState(stack, extracted);
                return InteractionResult.CONSUME;
            }
        } else if (cell.mass() > 0.0 && canFillGas()) {
            ChemicalState remainder = insertChemical(cell, false);
            if (remainder.mass() < cell.mass()) {
                ChemicalCellItem.setState(stack, remainder);
                return InteractionResult.CONSUME;
            }
        }
        return FluidUtil.interactWithFluidHandler(player, hand, fluidHandler) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    public boolean isPneumaticChemicalTube() {
        return getBlockState().getBlock() == LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get();
    }

    public PneumaticChemistryMode transportMode() {
        return transportMode;
    }

    public void setTransportMode(PneumaticChemistryMode mode) {
        PneumaticChemistryMode next = mode == null ? PneumaticChemistryMode.AIR : mode;
        if (!isPneumaticChemicalTube() || transportMode == next) return;
        transportMode = next;
        invalidateCaps();
        reviveCaps();
        setChanged();
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public void cycleTransportMode(Player player) {
        setTransportMode(transportMode.next());
        player.displayClientMessage(Component.translatable(
            "message.latent_chemlib.pneumatic_chemical_tube.mode." + transportMode.name().toLowerCase()
        ), true);
    }

    public IAirHandlerMachine pneumaticAirHandler() {
        return pneumaticAirHandler;
    }

    private void capture(ServerLevel level) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (!(neighbor instanceof ChemicalCloudBlockEntity cloud)) continue;
            ChemicalState cloudState = cloud.chemicalState();
            double amount = MachineTransfer.captureAmount(stored.mass(), cloudState.mass(), machineProfile().machineMassCapacity());
            if (amount <= 0.0) return;
            amount = Math.min(cloudState.mass(), Math.max(
                com.bettercontent.latentchemlib.sim.CloudInsertionService.MASS_PER_ADPOTHER_UNIT,
                Math.floor(amount / com.bettercontent.latentchemlib.sim.CloudInsertionService.MASS_PER_ADPOTHER_UNIT)
                    * com.bettercontent.latentchemlib.sim.CloudInsertionService.MASS_PER_ADPOTHER_UNIT
            ));
            ChemicalState moved = cloud.extractMass(amount);
            stored = stored.merge(moved);
            return;
        }
    }

    private void release(ServerLevel level) {
        if (stored.mass() <= 0.0) return;
        BlockPos target = worldPosition.above();
        ChemicalState moved = stored.withMass(Math.min(MachineTransfer.TRANSFER_MASS, stored.mass()));
        var inserted = com.bettercontent.latentchemlib.sim.CloudInsertionService.INSTANCE.insert(level, target, moved);
        if (inserted.acceptedAll()) {
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
        }
    }

    private void tickNativeAir() {
        if (!exposesNativeAir()) return;
        pneumaticAirHandler.setConnectedFaces(ALL_DIRECTIONS);
        pneumaticAirHandler.tick(this);
    }

    private void separateDryAir() {
        double available = Math.max(0.0, chemicalCapacityMass() - stored.mass());
        DryAirSeparation.separate(pneumaticAirHandler.getAir(), pneumaticAirHandler.getPressure(), available)
            .ifPresent(batch -> {
                pneumaticAirHandler.addAir(-batch.consumedNativeAir());
                stored = stored.merge(batch.output());
            });
    }

    private void balanceChemicalNeighbor(ServerLevel level) {
        Direction direction = Direction.values()[chemicalDirectionCursor++ % Direction.values().length];
        BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
        if (neighbor == null) return;
        neighbor.getCapability(LatentCapabilities.CHEMICAL_STATE, direction.getOpposite()).resolve().ifPresent(other -> {
            double ownCapacity = chemicalCapacityMass();
            double otherCapacity = other.chemicalCapacityMass();
            if (ownCapacity <= 0.0 || otherCapacity <= 0.0) return;
            double totalMass = chemicalState().mass() + other.chemicalState().mass();
            double desiredOwnMass = totalMass * ownCapacity / (ownCapacity + otherCapacity);
            if (chemicalState().mass() > desiredOwnMass) {
                transferChemical(this, other, Math.min(CHEMICAL_TRANSFER_MASS, chemicalState().mass() - desiredOwnMass));
            } else if (other.chemicalState().mass() > totalMass - desiredOwnMass) {
                transferChemical(other, this, Math.min(CHEMICAL_TRANSFER_MASS, other.chemicalState().mass() - (totalMass - desiredOwnMass)));
            }
        });
    }

    private static void transferChemical(IChemicalStateHandler source, IChemicalStateHandler target, double mass) {
        ChemicalState extracted = source.extractChemical(mass, false);
        if (extracted.mass() <= 0.0) return;
        ChemicalState remainder = target.insertChemical(extracted, false);
        if (remainder.mass() > 0.0) source.insertChemical(remainder, false);
    }

    public float getHeat() {
        return heat;
    }

    public float getMaxHeat() {
        return configuredMaxHeat();
    }

    public float getThermalCapacity() {
        return configuredMaxHeat();
    }

    public float getThermalResistance() {
        return 1.0f;
    }

    public boolean canConnect(Direction side) {
        return true;
    }

    public boolean canAdd(Direction side) {
        return true;
    }

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

    public float extractHeat(float amount, boolean simulate) {
        if (amount <= 0.0f) return 0.0f;
        float extracted = Math.min(amount, Math.max(0.0f, heat));
        if (!simulate && extracted > 0.0f) setHeat(heat - extracted);
        return extracted;
    }

    public void addHeat(float heat) {
        this.heat = Math.min(configuredMaxHeat(), Math.max(0.0f, this.heat + heat));
    }

    public void setHeat(float heat) {
        this.heat = Math.min(configuredMaxHeat(), Math.max(0.0f, heat));
    }

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
            || block == LatentChemlibMod.GAS_RELEASE.get()
            || (block == LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get() && transportMode == PneumaticChemistryMode.CHEMICAL);
    }

    private boolean canDrainGas() {
        Block block = getBlockState().getBlock();
        return block == LatentChemlibMod.GAS_CAPTURE.get()
            || block == LatentChemlibMod.GAS_TANK.get()
            || block == LatentChemlibMod.GAS_REACTION_CHAMBER.get()
            || block == LatentChemlibMod.DRY_AIR_SEPARATOR.get()
            || (block == LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get() && transportMode == PneumaticChemistryMode.CHEMICAL);
    }

    private boolean exposesNativeAir() {
        Block block = getBlockState().getBlock();
        return block == LatentChemlibMod.DRY_AIR_SEPARATOR.get()
            || (block == LatentChemlibMod.PNEUMATIC_CHEMICAL_TUBE.get() && transportMode == PneumaticChemistryMode.AIR);
    }

    private boolean exposesChemicalState() {
        return !isPneumaticChemicalTube() || transportMode == PneumaticChemistryMode.CHEMICAL;
    }

    @Override
    public ChemicalState chemicalState() {
        return stored;
    }

    @Override
    public double chemicalCapacityMass() {
        return Math.max(0.0, machineProfile().machineMassCapacity());
    }

    @Override
    public ChemicalState insertChemical(ChemicalState incoming, boolean simulate) {
        if (!exposesChemicalState() || !canFillGas() || incoming == null || incoming.mass() <= 0.0) return incoming == null ? ChemicalState.empty() : incoming;
        double acceptedMass = Math.min(incoming.mass(), Math.max(0.0, chemicalCapacityMass() - stored.mass()));
        if (acceptedMass <= 0.0) return incoming;
        ChemicalState.Split split = incoming.split(acceptedMass);
        if (!simulate) setStoredState(stored.merge(split.extracted()));
        return split.remainder();
    }

    @Override
    public ChemicalState extractChemical(double mass, boolean simulate) {
        if (!exposesChemicalState() || !canDrainGas() || !Double.isFinite(mass) || mass <= 0.0 || stored.mass() <= 0.0) return ChemicalState.empty();
        ChemicalState.Split split = stored.split(mass);
        if (!simulate) setStoredState(split.remainder());
        return split.extracted();
    }
}
