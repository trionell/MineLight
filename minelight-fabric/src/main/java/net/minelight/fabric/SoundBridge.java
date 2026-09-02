package net.minelight.fabric;

import net.minecraft.server.MinecraftServer;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.core.sound.SoundEngine;

/**
 * Feeds the engine's {@link SoundEngine} from the Minecraft world.
 *
 * <p>{@link #onSound} is called from {@code ServerLevelSoundMixin} for every
 * positional sound the server plays, and {@link #tick} mixes what each block
 * heard once per game tick. There is no audio to sample and no single global
 * level: each sound block is a microphone at its own coordinates, so what it
 * drives depends on where it was placed.</p>
 */
public final class SoundBridge {

    private final ConsoleEngine engine;

    public SoundBridge(ConsoleEngine engine) {
        this.engine = engine;
    }

    /**
     * A sound was played into the world.
     *
     * @param name  sound event path, e.g. {@code block.note_block.harp}
     * @param range how far the sound carries, from {@code SoundEvent.getRange}
     */
    public void onSound(double x, double y, double z,
                        String name, float volume, float pitch, float range) {
        engine.sound().onSound(x, y, z, name, volume, pitch, range);
    }

    public void tick(MinecraftServer server) {
        engine.sound().tick();
    }
}
