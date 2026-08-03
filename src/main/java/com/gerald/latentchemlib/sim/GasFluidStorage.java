package com.gerald.latentchemlib.sim;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One-species gas storage backed directly by a ChemicalState.
 */
public class GasFluidStorage implements IFluidHandler {
    private final Supplier<ChemicalState> stateGetter;
    private final Consumer<ChemicalState> stateSetter;
    private final IntSupplier capacityGetter;
    private final Supplier<Boolean> canFill;
    private final Supplier<Boolean> canDrain;

    public GasFluidStorage(
        Supplier<ChemicalState> stateGetter,
        Consumer<ChemicalState> stateSetter,
        IntSupplier capacityGetter,
        Supplier<Boolean> canFill,
        Supplier<Boolean> canDrain
    ) {
        this.stateGetter = stateGetter;
        this.stateSetter = stateSetter;
        this.capacityGetter = capacityGetter;
        this.canFill = canFill;
        this.canDrain = canDrain;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0) return FluidStack.EMPTY;
        ChemicalState state = stateGetter.get();
        return GasFluidCodec.fluidFromState(state, GasFluidCodec.millibucketsForMass(state.mass()));
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? Math.max(0, capacityGetter.getAsInt()) : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        if (tank != 0 || !canFill.get()) return false;
        Optional<ChemicalState> incoming = GasFluidCodec.stateFromFluid(stack);
        if (incoming.isEmpty()) return false;
        ChemicalState stored = stateGetter.get();
        return stored.mass() <= 0.0 || (stored.isPure() && stored.chemicalId().equals(incoming.get().chemicalId()));
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (!canFill.get() || resource.isEmpty()) return 0;
        Optional<ChemicalState> decoded = GasFluidCodec.stateFromFluid(resource);
        if (decoded.isEmpty()) return 0;
        ChemicalState stored = stateGetter.get();
        ChemicalState incoming = decoded.get();
        if (stored.mass() > 0.0 && (!stored.isPure() || !stored.chemicalId().equals(incoming.chemicalId()))) return 0;
        double remainingCapacityMass = Math.max(
            0.0,
            GasFluidCodec.massForMillibuckets(getTankCapacity(0)) - stored.mass()
        );
        int accepted = Math.min(resource.getAmount(), GasFluidCodec.millibucketsForMass(remainingCapacityMass));
        if (accepted <= 0) return 0;
        if (action.execute()) {
            stateSetter.accept(stored.merge(incoming.withMass(GasFluidCodec.massForMillibuckets(accepted))));
        }
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (!canDrain.get() || resource.isEmpty()) return FluidStack.EMPTY;
        ChemicalState stored = stateGetter.get();
        FluidStack available = GasFluidCodec.fluidFromState(stored, resource.getAmount());
        if (available.isEmpty() || !available.isFluidEqual(resource)) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (!canDrain.get() || maxDrain <= 0) return FluidStack.EMPTY;
        ChemicalState stored = stateGetter.get();
        FluidStack drained = GasFluidCodec.fluidFromState(stored, maxDrain);
        if (drained.isEmpty()) return FluidStack.EMPTY;
        if (action.execute()) {
            double remainingMass = Math.max(0.0, stored.mass() - GasFluidCodec.massForMillibuckets(drained.getAmount()));
            stateSetter.accept(stored.withMass(remainingMass));
        }
        return drained;
    }
}
