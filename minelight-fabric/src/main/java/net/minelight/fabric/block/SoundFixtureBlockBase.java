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
import net.minelight.core.sound.SoundEngine;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Base for sound-reactive fixture blocks. Right-click opens the config screen
 * (radius, gain, channel map); breaking the block unregisters it.
 */
public abstract class SoundFixtureBlockBase extends Block implements EntityBlock {

    private final SoundEngine.Mode mode;

    protected SoundFixtureBlockBase(Properties settings, SoundEngine.Mode mode) {
        super(settings);
        this.mode = mode;
    }

    public SoundEngine.Mode mode() {
        return mode;
    }

    @Override
    public abstract SoundBlockEntity newBlockEntity(BlockPos pos, BlockState state);

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                                 Player player, BlockHitResult hit) {
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof SoundBlockEntity be) {
            player.openMenu(be);
        }
        return InteractionResult.CONSUME;
    }


    // Nothing ticks a block entity unless its block hands out a ticker, and
    // without ticking SoundBlockEntity never registers with the engine.
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (l, pos, st, be) -> {
            if (be instanceof SoundBlockEntity fixture) {
                fixture.tick();
            }
        };
    }

}
