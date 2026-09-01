package net.minelight.fabric.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Sound-meter fixture: a virtual microphone. The louder the nearby in-game
 * sound, the higher the dimmer level — with a fast attack and smooth decay so
 * it breathes like a real VU meter.
 */
public final class SoundMeterBlock extends SoundFixtureBlockBase {

    public SoundMeterBlock(Settings settings) {
        super(settings, net.minelight.core.sound.SoundEngine.Mode.LEVEL);
    }

    @Override
    public SoundBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SoundBlockEntity(pos, state,
                net.minelight.core.sound.SoundEngine.Mode.LEVEL);
    }
}
