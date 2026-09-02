package net.minelight.core.sound;

/**
 * Guesses the frequency content of a Minecraft sound.
 *
 * <p>The server has no audio: it knows a sound event's name, its volume and
 * its pitch multiplier, and nothing about the samples the client will play.
 * SPECTRUM blocks still need something to split into bass/mid/treble, so we
 * estimate a fundamental from the event name and scale it by pitch.</p>
 *
 * <p>The numbers below are ear estimates of the vanilla assets, not measured
 * spectra. They only have to be ordered correctly relative to each other —
 * an explosion below a footstep below breaking glass — for the hue of a
 * SPECTRUM block to track what is happening.</p>
 */
public final class SoundSpectrum {

    private SoundSpectrum() {
    }

    /**
     * Name fragment to fundamental in Hz, scanned in order so the first match
     * wins. More specific fragments must come first: {@code note_block.hat}
     * before the generic {@code note_block.}.
     */
    private static final Object[][] FUNDAMENTALS = {
            // note blocks first: their fragments are the most specific, and
            // pitch below turns the base into the note actually played
            {"note_block.basedrum", 80.0},
            {"note_block.didgeridoo", 90.0},
            {"note_block.bass", 110.0},
            {"note_block.guitar", 160.0},
            {"note_block.snare", 400.0},
            {"note_block.bell", 1200.0},
            {"note_block.chime", 1500.0},
            {"note_block.hat", 3000.0},
            {"note_block.", 370.0},   // harp and friends sit around F#4
            // loud lows
            {"lightning", 50.0},
            {"explode", 70.0},
            {"warden", 100.0},
            {"ravager", 120.0},
            {"piston", 150.0},
            // bright materials, before the generic actions below so that
            // breaking glass is not filed under "break"
            {"glass", 2500.0},
            {"amethyst", 1600.0},
            {"item.pickup", 1500.0},
            {"chime", 1500.0},
            {"experience_orb", 1400.0},
            {"bell", 1200.0},
            // generic actions and materials
            {"door", 180.0},
            {"anvil", 200.0},
            {"fall", 220.0},
            {"step", 250.0},
            {"dig", 260.0},
            {"lava", 300.0},
            {"break", 320.0},
            {"villager", 500.0},
            {"water", 500.0},
            {"fire", 600.0},
    };

    /** Fallback for every sound with no entry above. */
    private static final double DEFAULT_HZ = 440.0;

    private static final double LOW_HZ = 50.0;
    private static final double HIGH_HZ = 6000.0;

    /**
     * Estimated fundamental of {@code name} in Hz, shifted by {@code pitch}.
     *
     * @param name  sound event id, with or without a namespace
     *              ({@code block.note_block.harp})
     * @param pitch the playback pitch multiplier, 1.0 for unshifted
     */
    public static double frequency(String name, double pitch) {
        double base = DEFAULT_HZ;
        if (name != null) {
            String id = name.toLowerCase();
            for (Object[] row : FUNDAMENTALS) {
                if (id.contains((String) row[0])) {
                    base = (Double) row[1];
                    break;
                }
            }
        }
        return base * (pitch > 0 ? pitch : 1.0);
    }

    /**
     * Split one frequency across bass/mid/treble.
     *
     * <p>A three-point crossfade on a log scale rather than hard crossovers:
     * the weights always sum to 1 and slide continuously, so a rising pitch
     * sweeps a SPECTRUM block's hue instead of stepping between three
     * colours.</p>
     *
     * @return {@code {bass, mid, treble}}, summing to 1
     */
    public static double[] bandWeights(double frequency) {
        double span = Math.log(HIGH_HZ / LOW_HZ);
        double t = Math.clamp(Math.log(Math.max(frequency, 1.0) / LOW_HZ) / span, 0.0, 1.0);
        if (t <= 0.5) {
            return new double[]{1 - 2 * t, 2 * t, 0};
        }
        return new double[]{0, 2 - 2 * t, 2 * t - 1};
    }

    /**
     * The note a note block was playing, from the pitch on its sound event.
     * Vanilla plays note {@code n} at {@code 2^((n-12)/12)}.
     *
     * @return note 0–24
     */
    public static int noteFromPitch(double pitch) {
        if (pitch <= 0) {
            return 0;
        }
        int note = (int) Math.round(12 + 12 * (Math.log(pitch) / Math.log(2)));
        return Math.clamp(note, 0, 24);
    }
}
