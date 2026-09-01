package net.minelight.fabric.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Beat fixture: detects sudden onsets (kicks, explosions, note-block attacks)
 * and fires a full-scale bump / event on each beat. The classic
 * sound-to-light strobe trigger.
 */
public final class BeatBlock extends SoundFixtureBlockBase {

    public BeatBlock(Properties settings) {
        super(settings, net.minelight.core.sound.SoundEngine.Mode.BEAT);
    }

    @Override
    public SoundBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoundBlockEntity(pos, state,
                net.minelight.core.sound.SoundEngine.Mode.BEAT);
    }
}
