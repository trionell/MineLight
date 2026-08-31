package net.minelight.fabric.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minelight.core.sound.SoundEngine;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Base for sound-reactive fixture blocks. Right-click opens the config screen
 * (radius, gain, channel map); breaking the block unregisters it.
 */
public abstract class SoundFixtureBlockBase extends Block implements BlockEntityProvider {

    private final SoundEngine.Mode mode;

    protected SoundFixtureBlockBase(Settings settings, SoundEngine.Mode mode) {
        super(settings);
        this.mode = mode;
    }

    public SoundEngine.Mode mode() {
        return mode;
    }

    @Override
    public abstract SoundBlockEntity createBlockEntity(BlockPos pos, BlockState state);

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof SoundBlockEntity be) {
            player.openHandledScreen(be);
        }
        return ActionResult.CONSUME;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos,
                                   BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof SoundBlockEntity be) {
                be.unregister();
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
