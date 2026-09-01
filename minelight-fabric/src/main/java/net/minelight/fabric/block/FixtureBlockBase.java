package net.minelight.fabric.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minelight.fabric.blockentity.FixtureBlockEntity;

/**
 * Base class for all MineLight fixture blocks.
 *
 * <p>A fixture block is the player's physical handle on a lighting fixture:
 * feed it redstone and it drives a DMX channel (via a console); right-click
 * it to configure which channel / event it targets. Subclasses differ in how
 * many inputs they read and what they do with them.</p>
 */
public abstract class FixtureBlockBase extends Block implements EntityBlock {

    private final net.minelight.core.api.FixtureBlock.Type engineType;

    protected FixtureBlockBase(Properties settings, net.minelight.core.api.FixtureBlock.Type engineType) {
        super(settings);
        this.engineType = engineType;
    }

    public net.minelight.core.api.FixtureBlock.Type engineType() {
        return engineType;
    }

    @Override
    public FixtureBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FixtureBlockEntity(pos, state, engineType);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                                 Player player, BlockHitResult hit) {
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof FixtureBlockEntity be) {
            player.openMenu(be);
        }
        return InteractionResult.CONSUME;
    }


    // Nothing ticks a block entity unless its block hands out a ticker, and
    // without ticking FixtureBlockEntity never registers with the engine.
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (l, pos, st, be) -> {
            if (be instanceof FixtureBlockEntity fixture) {
                fixture.tick();
            }
        };
    }

}
