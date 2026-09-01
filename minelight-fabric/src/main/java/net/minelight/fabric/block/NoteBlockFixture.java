package net.minelight.fabric.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Note-block fixture: listens for note blocks played within its radius and
 * maps pitch → hue (RGB) and instrument → accent. Play a melody, paint the
 * room with colour.
 */
public final class NoteBlockFixture extends SoundFixtureBlockBase {

    public NoteBlockFixture(Properties settings) {
        super(settings, net.minelight.core.sound.SoundEngine.Mode.NOTE);
    }

    @Override
    public SoundBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoundBlockEntity(pos, state,
                net.minelight.core.sound.SoundEngine.Mode.NOTE);
    }
}
