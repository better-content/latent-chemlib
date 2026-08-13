package com.bettercontent.latentchemlib.block;

import com.bettercontent.latentchemlib.LatentChemlibMod;
import com.bettercontent.latentchemlib.blockentity.LatentMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class LatentMachineBlock extends BaseEntityBlock {
    public LatentMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LatentMachineBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof LatentMachineBlockEntity machine) {
            if (machine.isPneumaticChemicalTube() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
                if (!level.isClientSide) machine.cycleTransportMode(player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return machine.useHeldCell(player, hand);
        }
        return InteractionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, LatentChemlibMod.MACHINE_ENTITY.get(), LatentMachineBlockEntity::tick);
    }
}
