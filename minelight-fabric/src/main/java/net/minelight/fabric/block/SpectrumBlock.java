package net.minelight.fabric.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Spectrum fixture: splits nearby sound into bass / mid / treble energy and
 * drives R / G / B channels, so a bass drop goes red and a hi-hat shimmer
 * goes blue.
 */
public final class SpectrumBlock extends SoundFixtureBlockBase {

    public SpectrumBlock(Settings settings) {
        super(settings, net.minelight.core.sound.SoundEngine.Mode.SPECTRUM);
    }

    @Override
    public SoundBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SoundBlockEntity(pos, state,
                net.minelight.core.sound.SoundEngine.Mode.SPECTRUM);
    }
}
