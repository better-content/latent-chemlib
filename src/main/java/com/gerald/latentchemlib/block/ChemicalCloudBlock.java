package com.gerald.latentchemlib.block;

import com.gerald.latentchemlib.LatentChemlibMod;
import com.gerald.latentchemlib.blockentity.ChemicalCloudBlockEntity;
import com.gerald.latentchemlib.sim.ChemicalCloudVisuals;
import com.gerald.latentchemlib.sim.ChemicalState;
import com.gerald.latentchemlib.sim.GasFluidCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class ChemicalCloudBlock extends BaseEntityBlock {
    public static final IntegerProperty DIFFUSION = IntegerProperty.create("diffusion", 0, 3);

    public ChemicalCloudBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DIFFUSION, 3));
    }

    public static int diffusionTier(ChemicalState state) {
        return ChemicalCloudVisuals.diffusionTier(state);
    }

    public static int lightLevel(BlockState state) {
        return 7;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChemicalCloudBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(LatentChemlibMod.SEALED_CHEMICAL_CELL.get())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ChemicalCloudBlockEntity cloud)) return InteractionResult.PASS;
        int available = GasFluidCodec.millibucketsForMass(cloud.chemicalState().mass());
        FluidStack resource = GasFluidCodec.fluidFromState(cloud.chemicalState(), available);
        if (resource.isEmpty()) return InteractionResult.PASS;
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(handler -> {
            int accepted = handler.fill(resource, IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) return InteractionResult.PASS;
            FluidStack acceptedResource = resource.copy();
            acceptedResource.setAmount(accepted);
            int filled = handler.fill(acceptedResource, IFluidHandler.FluidAction.EXECUTE);
            if (filled <= 0) return InteractionResult.PASS;
            cloud.extractMass(GasFluidCodec.massForMillibuckets(filled));
            if (cloud.chemicalState().mass() <= 0.0) {
                level.removeBlock(pos, false);
            }
            return InteractionResult.CONSUME;
        }).orElse(InteractionResult.PASS);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(DIFFUSION);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, LatentChemlibMod.CHEMICAL_CLOUD_ENTITY.get(), ChemicalCloudBlockEntity::tick);
    }
}
