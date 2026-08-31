package net.minelight.fabric.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Beat fixture: detects sudden onsets (kicks, explosions, note-block attacks)
 * and fires a full-scale bump / event on each beat. The classic
 * sound-to-light strobe trigger.
 */
public final class BeatBlock extends SoundFixtureBlockBase {

    public BeatBlock(Settings settings) {
        super(settings, net.minelight.core.sound.SoundEngine.Mode.BEAT);
    }

    @Override
    public SoundBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SoundBlockEntity(pos, state,
                net.minelight.core.sound.SoundEngine.Mode.BEAT);
    }
}
