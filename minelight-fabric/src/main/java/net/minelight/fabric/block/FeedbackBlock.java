package net.minelight.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minelight.core.api.FixtureBlock;

/**
 * Feedback fixture block: the reverse of the others — it <em>receives</em> a
 * level from the console and emits redstone power proportional to it (0–15).
 *
 * <p>Use it to drive in-game contraptions from the lighting desk: a fader at
 * 50% powers a piston halfway, a cue at full opens a door, a blackout kills a
 * beacon beam.</p>
 */
public final class FeedbackBlock extends FixtureBlockBase {

    public FeedbackBlock(Properties settings) {
        super(settings, FixtureBlock.Type.FEEDBACK);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(BlockStateProperties.POWER);
    }
}
