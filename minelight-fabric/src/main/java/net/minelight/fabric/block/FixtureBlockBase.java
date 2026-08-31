package net.minelight.fabric.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minelight.fabric.blockentity.FixtureBlockEntity;

/**
 * Base class for all MineLight fixture blocks.
 *
 * <p>A fixture block is the player's physical handle on a lighting fixture:
 * feed it redstone and it drives a DMX channel (via a console); right-click
 * it to configure which channel / event it targets. Subclasses differ in how
 * many inputs they read and what they do with them.</p>
 */
public abstract class FixtureBlockBase extends Block implements BlockEntityProvider {

    private final net.minelight.core.api.FixtureBlock.Type engineType;

    protected FixtureBlockBase(Settings settings, net.minelight.core.api.FixtureBlock.Type engineType) {
        super(settings);
        this.engineType = engineType;
    }

    public net.minelight.core.api.FixtureBlock.Type engineType() {
        return engineType;
    }

    @Override
    public FixtureBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FixtureBlockEntity(pos, state, engineType);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof FixtureBlockEntity be) {
            player.openHandledScreen(be);
        }
        return ActionResult.CONSUME;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos,
                                   BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof FixtureBlockEntity be) {
                be.unregister();
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
