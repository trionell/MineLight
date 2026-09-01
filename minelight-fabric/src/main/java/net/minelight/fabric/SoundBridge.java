package net.minelight.fabric;

import net.minecraft.server.MinecraftServer;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.core.sound.SoundEngine;

/**
 * Feeds the engine's {@link SoundEngine} from the Minecraft world.
 *
 * <p>Two inputs:</p>
 * <ul>
 *   <li><b>Note blocks</b> — {@link #onNoteBlockPlayed} is called from a
 *       note-block mixin / event hook when a note block actually plays, and
 *       forwards pitch + instrument to NOTE fixtures in range.</li>
 *   <li><b>Ambient sound level</b> — {@link #bump} is called by world-sound
 *       hooks (explosions, pistons, note blocks, mob sounds) to raise a
 *       coarse 0–1 level, which is fed each tick to LEVEL, BEAT, and SPECTRUM
 *       fixtures. Louder moments in the world drive the lights harder.</li>
 * </ul>
 */
public final class SoundBridge {

    private final ConsoleEngine engine;

    /** Running ambient level 0–1, decayed each tick and bumped by world sound. */
    private double ambient;

    public SoundBridge(ConsoleEngine engine) {
        this.engine = engine;
    }

    /** Raise the ambient level (explosions, pistons, note blocks, mob sounds). */
    public void bump(double amount) {
        ambient = Math.min(1.0, ambient + amount);
    }

    /**
     * Called when a note block plays. Forwards pitch/instrument to every NOTE
     * fixture in range.
     */
    public void onNoteBlockPlayed(int x, int y, int z, int note, String instrument) {
        engine.sound().onNote(x, y, z, note, instrument);
        bump(0.4); // a note is also ambient energy
    }

    public void tick(MinecraftServer server) {
        engine.sound().tick();

        // decay ambient level, then feed amplitude-driven fixtures
        ambient = Math.max(0, ambient - 0.05);
        for (var b : engine.sound().all()) {
            switch (b.mode) {
                case LEVEL, BEAT -> engine.sound().onSample(b.id, ambient, 0, 0, 0);
                case SPECTRUM -> engine.sound().onSample(b.id, ambient,
                        ambient, ambient * 0.6, ambient * 0.3); // crude band split
                case NOTE -> {
                    // event-driven via onNoteBlockPlayed
                }
            }
        }
    }
}
