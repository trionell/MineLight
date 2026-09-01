package net.minelight.fabric.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Sound-meter fixture: a virtual microphone. The louder the nearby in-game
 * sound, the higher the dimmer level — with a fast attack and smooth decay so
 * it breathes like a real VU meter.
 */
public final class SoundMeterBlock extends SoundFixtureBlockBase {

    public SoundMeterBlock(Properties settings) {
        super(settings, net.minelight.core.sound.SoundEngine.Mode.LEVEL);
    }

    @Override
    public SoundBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoundBlockEntity(pos, state,
                net.minelight.core.sound.SoundEngine.Mode.LEVEL);
    }
}
