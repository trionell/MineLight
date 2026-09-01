package net.minelight.fabric.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Note-block fixture: listens for note blocks played within its radius and
 * maps pitch → hue (RGB) and instrument → accent. Play a melody, paint the
 * room with colour.
 */
public final class NoteBlockFixture extends SoundFixtureBlockBase {

    public NoteBlockFixture(Settings settings) {
        super(settings, net.minelight.core.sound.SoundEngine.Mode.NOTE);
    }

    @Override
    public SoundBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SoundBlockEntity(pos, state,
                net.minelight.core.sound.SoundEngine.Mode.NOTE);
    }
}
