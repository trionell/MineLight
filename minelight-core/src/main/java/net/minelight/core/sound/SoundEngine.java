package net.minelight.core.sound;

import net.minelight.core.api.GameEvent;
import net.minelight.core.engine.ConsoleEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Sound-to-light engine.
 *
 * <p>The console-agnostic half of MineLight's sound-reactive blocks. It does
 * not touch Minecraft audio itself — the Fabric bridge feeds it the sound
 * events the server originates:</p>
 *
 * <ul>
 *   <li><b>Positional sounds</b> ({@link #onSound}) — a name, a position, a
 *       volume and a pitch. Every block acts as a microphone at its own
 *       position: a sound is attenuated by its distance to the block, gated
 *       by the block's {@code radius}, and summed with everything else heard
 *       during the same tick.</li>
 *   <li><b>Discrete note events</b> ({@link #onNote}) — pitch + instrument,
 *       derived from note-block sounds.</li>
 *   <li><b>Raw amplitude samples</b> ({@link #onSample}) — a 0–1 level and
 *       bands, for feeding the analyzers directly.</li>
 * </ul>
 *
 * <p>Each registered sound block runs its input through an analyzer and writes
 * DMX (or fires events) just like a redstone fixture block:</p>
 *
 * <ul>
 *   <li>{@link Mode#NOTE} — pitch → hue (RGB) or channel bump</li>
 *   <li>{@link Mode#LEVEL} — amplitude → dimmer, with attack/decay envelope</li>
 *   <li>{@link Mode#BEAT} — onset (sudden rise) → event / full-scale bump</li>
 *   <li>{@link Mode#SPECTRUM} — bass/mid/treble energy → R/G/B channels</li>
 * </ul>
 */
public final class SoundEngine {

    private final ConsoleEngine engine;
    private final Map<Integer, SoundBlock> blocks = new ConcurrentHashMap<>();
    private int nextId = 1;

    public enum Mode {
        NOTE(net.minelight.core.api.BlockRole.INPUT),
        LEVEL(net.minelight.core.api.BlockRole.INPUT),
        BEAT(net.minelight.core.api.BlockRole.TRIGGER),
        SPECTRUM(net.minelight.core.api.BlockRole.INPUT);

        private final net.minelight.core.api.BlockRole role;

        Mode(net.minelight.core.api.BlockRole role) {
            this.role = role;
        }

        /** Which way signal flows through a block in this mode. */
        public net.minelight.core.api.BlockRole role() {
            return role;
        }
    }

    /**
     * A sound-reactive block's config + runtime state.
     */
    public static final class SoundBlock {
        public final int id;
        public final String name;
        public final Mode mode;
        public final int x, y, z;
        /** Detection radius in blocks (for the Fabric listener). */
        public int radius = 8;
        /** Output universe. */
        public int universe = 1;
        /** Base DMX channel (meaning depends on mode). */
        public int channel = 1;
        /** Sensitivity / gain 0.1–10. */
        public double gain = 1.0;
        /** Decay per tick for LEVEL envelope (0–1, higher = faster falloff). */
        public double decay = 0.15;
        /** Beat threshold: onset must exceed running average by this factor. */
        public double beatThreshold = 1.6;

        // runtime state
        double envelope;
        double runningAvg;
        boolean beatHigh;
        /**
         * Energy heard since the last tick, summed as power so that two
         * sounds of equal level add to about 1.4x rather than 2x. Drained
         * and rooted back into an amplitude by {@link #tick()}.
         */
        final DoubleAdder energy = new DoubleAdder();
        final DoubleAdder bassEnergy = new DoubleAdder();
        final DoubleAdder midEnergy = new DoubleAdder();
        final DoubleAdder trebleEnergy = new DoubleAdder();
        /** Last value announced on the event bus, to keep silence quiet. */
        int lastEmitted = -1;
        /** Last sample fed in, before gain. */
        volatile double lastLevel;
        /** Last note seen by a NOTE block, -1 when none yet. */
        volatile int lastNote = -1;
        volatile String lastInstrument = "";
        volatile long lastActivity;

        public double envelope() {
            return envelope;
        }

        public double runningAvg() {
            return runningAvg;
        }

        public boolean beatHigh() {
            return beatHigh;
        }

        public double lastLevel() {
            return lastLevel;
        }

        public int lastNote() {
            return lastNote;
        }

        public String lastInstrument() {
            return lastInstrument;
        }

        public long lastActivity() {
            return lastActivity;
        }

        SoundBlock(int id, String name, Mode mode, int x, int y, int z) {
            this.id = id;
            this.name = name;
            this.mode = mode;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public SoundEngine(ConsoleEngine engine) {
        this.engine = engine;
    }

    public synchronized SoundBlock add(Mode mode, String name, int x, int y, int z) {
        int id = nextId++;
        SoundBlock b = new SoundBlock(id, name, mode, x, y, z);
        blocks.put(id, b);
        return b;
    }

    public boolean remove(int id) {
        return blocks.remove(id) != null;
    }

    public SoundBlock byId(int id) {
        return blocks.get(id);
    }

    public java.util.List<SoundBlock> all() {
        return java.util.List.copyOf(blocks.values());
    }

    public int size() {
        return blocks.size();
    }

    // ---- input: discrete note-block events ---------------------------------

    /**
     * A note block was played near a sound block.
     *
     * @param x, y, z    position of the note block
     * @param note       MIDI-style note 0–24 (F#3..F#5 in vanilla)
     * @param instrument instrument name ("harp", "bass", "pling", ...)
     */
    public void onNote(int x, int y, int z, int note, String instrument) {
        for (SoundBlock b : blocks.values()) {
            if (b.mode != Mode.NOTE || !inRange(b, x, y, z)) {
                continue;
            }
            b.lastNote = note;
            b.lastInstrument = instrument;
            b.lastActivity = System.currentTimeMillis();

            // pitch -> hue: map 0-24 across the colour wheel
            int[] rgb = pitchToRgb(note);
            engine.setDmx(b.universe, b.channel, rgb[0]);
            engine.setDmx(b.universe, b.channel + 1, rgb[1]);
            engine.setDmx(b.universe, b.channel + 2, rgb[2]);

            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("blockId", b.id);
            data.put("blockName", b.name);
            data.put("note", note);
            data.put("instrument", instrument);
            data.put("r", rgb[0]);
            data.put("g", rgb[1]);
            data.put("b", rgb[2]);
            engine.emit(new GameEvent("sound.note", data));
        }
    }

    // ---- input: positional sounds -------------------------------------------

    /**
     * A sound the server just played into the world.
     *
     * <p>Every block is a microphone standing at its own position. The sound
     * is attenuated linearly over its own audible range — the same rolloff
     * the client uses — and dropped entirely beyond the block's
     * {@code radius}, so where a block is placed decides what it hears.</p>
     *
     * <p>Volume above 1 buys range rather than level: that is what Minecraft
     * does with it, and it stops a distant explosion from pinning every meter
     * on the server.</p>
     *
     * @param name   sound event id, e.g. {@code block.note_block.harp}
     * @param volume the volume the sound was played at (may exceed 1)
     * @param pitch  playback pitch multiplier, 1.0 for unshifted
     * @param range  how far the sound carries, in blocks
     */
    public void onSound(double x, double y, double z,
                        String name, double volume, double pitch, double range) {
        if (range <= 0 || volume <= 0) {
            return;
        }
        // A note block is both a note and a sound: NOTE blocks get the pitch
        // and instrument, everything else just hears it.
        int noteBlock = name == null ? -1 : name.indexOf("note_block.");
        if (noteBlock >= 0) {
            onNote((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                    SoundSpectrum.noteFromPitch(pitch),
                    name.substring(noteBlock + "note_block.".length()));
        }

        double[] bands = SoundSpectrum.bandWeights(SoundSpectrum.frequency(name, pitch));
        double amplitude = Math.min(1.0, volume);
        for (SoundBlock b : blocks.values()) {
            if (b.mode == Mode.NOTE) {
                continue;
            }
            double dx = x - (b.x + 0.5), dy = y - (b.y + 0.5), dz = z - (b.z + 0.5);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > b.radius || dist >= range) {
                continue;
            }
            double level = amplitude * (1.0 - dist / range);
            double power = level * level;
            b.energy.add(power);
            b.bassEnergy.add(power * bands[0]);
            b.midEnergy.add(power * bands[1]);
            b.trebleEnergy.add(power * bands[2]);
        }
    }

    // ---- input: amplitude samples -------------------------------------------

    /**
     * An amplitude sample for a sound block. For LEVEL/BEAT blocks this is a
     * single 0–1 level; for SPECTRUM blocks bass/mid/treble carry band energy.
     */
    public void onSample(int blockId, double level, double bass, double mid, double treble) {
        SoundBlock b = blocks.get(blockId);
        if (b == null) {
            return;
        }
        process(b, level, bass, mid, treble);
    }

    private void process(SoundBlock b, double level, double bass, double mid, double treble) {
        b.lastLevel = level;
        if (level > 0.01) {
            b.lastActivity = System.currentTimeMillis();
        }
        switch (b.mode) {
            case LEVEL -> processLevel(b, level);
            case BEAT -> processBeat(b, level);
            case SPECTRUM -> processSpectrum(b, bass, mid, treble);
            case NOTE -> {
                // note blocks use onNote()
            }
        }
    }

    private void processLevel(SoundBlock b, double level) {
        double target = clamp01(level * b.gain);
        // fast attack, slow decay
        if (target > b.envelope) {
            b.envelope = target;
        } else {
            b.envelope = Math.max(target, b.envelope - b.decay);
        }
        int value = (int) Math.round(b.envelope * 255);
        engine.setDmx(b.universe, b.channel, value);
        emitLevelChange("sound.level", b, value);
    }

    private void processBeat(SoundBlock b, double level) {
        double v = clamp01(level * b.gain);
        // Compare against the average as it stood *before* this sample.
        // Seeding the average with the first sample made a lone transient
        // measure itself, so the first hit after a quiet start was swallowed.
        boolean onset = v > b.runningAvg * b.beatThreshold && v > 0.15;
        b.runningAvg = b.runningAvg * 0.9 + v * 0.1;
        if (onset && !b.beatHigh) {
            b.beatHigh = true;
            b.lastActivity = System.currentTimeMillis();
            engine.setDmx(b.universe, b.channel, 255);
            emitSoundEvent("sound.beat", b, 255);
        } else if (!onset && b.beatHigh) {
            b.beatHigh = false;
            engine.setDmx(b.universe, b.channel, 0);
        }
    }

    private void processSpectrum(SoundBlock b, double bass, double mid, double treble) {
        int r = (int) Math.round(clamp01(bass * b.gain) * 255);
        int g = (int) Math.round(clamp01(mid * b.gain) * 255);
        int bl = (int) Math.round(clamp01(treble * b.gain) * 255);
        engine.setDmx(b.universe, b.channel, r);
        engine.setDmx(b.universe, b.channel + 1, g);
        engine.setDmx(b.universe, b.channel + 2, bl);
        emitLevelChange("sound.spectrum", b, (r + g + bl) / 3);
    }

    /**
     * Advance one game tick: mix everything each block heard since the last
     * one and run it through that block's analyzer.
     *
     * <p>This is the only place LEVEL envelopes advance. They used to decay
     * here <em>and</em> in {@code processLevel}, which halved every decay
     * time the user set.</p>
     */
    public void tick() {
        for (SoundBlock b : blocks.values()) {
            if (b.mode == Mode.NOTE) {
                continue;
            }
            double level = b.energy.sumThenReset();
            double bass = b.bassEnergy.sumThenReset();
            double mid = b.midEnergy.sumThenReset();
            double treble = b.trebleEnergy.sumThenReset();
            if (level == 0 && atRest(b)) {
                continue;
            }
            process(b, Math.sqrt(level), Math.sqrt(bass), Math.sqrt(mid), Math.sqrt(treble));
        }
    }

    /**
     * Whether a silent block has finished settling. A block that hears nothing
     * and has nothing left to decay writes no DMX and emits no events, so a
     * show with no sound in it costs nothing.
     */
    private static boolean atRest(SoundBlock b) {
        return switch (b.mode) {
            case LEVEL -> b.envelope <= 0;
            case SPECTRUM -> b.lastEmitted <= 0;
            // BEAT only writes on an edge, but its baseline still has to fall
            // during silence or the next hit measures against a stale average.
            case BEAT -> !b.beatHigh && b.runningAvg <= 0.001;
            case NOTE -> true;
        };
    }

    /**
     * Announce a level, but only when it moved. Silence is the common case —
     * a block hears nothing most ticks — so a held value emits nothing rather
     * than pushing an identical event onto the bus 20 times a second. BEAT
     * does not come through here: it emits on its own rising edge.
     */
    private void emitLevelChange(String kind, SoundBlock b, int value) {
        if (b.lastEmitted == value) {
            return;
        }
        b.lastEmitted = value;
        emitSoundEvent(kind, b, value);
    }

    private void emitSoundEvent(String kind, SoundBlock b, int value) {
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("blockId", b.id);
        data.put("blockName", b.name);
        data.put("mode", b.mode.name());
        data.put("value", value);
        engine.emit(new GameEvent(kind, data));
    }

    private static boolean inRange(SoundBlock b, int x, int y, int z) {
        int dx = x - b.x, dy = y - b.y, dz = z - b.z;
        return dx * dx + dy * dy + dz * dz <= b.radius * b.radius;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /**
     * Map a note-block note (0–24) to an RGB colour by walking the hue wheel.
     * One turn per octave so the scale repeats each octave.
     */
    static int[] pitchToRgb(int note) {
        double hue = (note % 12) / 12.0;
        return hslToRgb(hue, 1.0, 0.5);
    }

    /** Simple HSL -> RGB, all inputs 0–1, output 0–255. */
    static int[] hslToRgb(double h, double s, double l) {
        double r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + 1.0 / 3);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0 / 3);
        }
        return new int[]{(int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255)};
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }

    // ---- monitoring -----------------------------------------------------------

    /**
     * A monitoring snapshot: config plus what each block is doing right now.
     *
     * <p>Output levels come from the merged DMX buffer, so this reports what
     * would leave for a desk rather than what the analyzer last computed.</p>
     */
    public com.google.gson.JsonArray liveJson() {
        Map<Integer, byte[]> dmx = engine.dmxSnapshot();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (SoundBlock b : blocks.values()) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("id", b.id);
            o.addProperty("name", b.name);
            o.addProperty("mode", b.mode.name());
            o.addProperty("role", b.mode.role().name());
            o.addProperty("sound", true);
            o.addProperty("x", b.x);
            o.addProperty("y", b.y);
            o.addProperty("z", b.z);
            o.addProperty("radius", b.radius);
            o.addProperty("universe", b.universe);
            o.addProperty("channel", b.channel);
            o.addProperty("gain", b.gain);
            o.addProperty("decay", b.decay);
            o.addProperty("beatThreshold", b.beatThreshold);
            o.addProperty("level", b.lastLevel);
            o.addProperty("envelope", b.envelope);
            o.addProperty("runningAvg", b.runningAvg);
            o.addProperty("beat", b.beatHigh);
            o.addProperty("lastActivity", b.lastActivity);
            if (b.mode == Mode.NOTE) {
                o.addProperty("lastNote", b.lastNote);
                o.addProperty("lastInstrument", b.lastInstrument);
            }
            // NOTE and SPECTRUM blocks write R/G/B from the base channel up;
            // the others use a single channel.
            int width = b.mode == Mode.NOTE || b.mode == Mode.SPECTRUM ? 3 : 1;
            com.google.gson.JsonArray out = new com.google.gson.JsonArray();
            for (int i = 0; i < width; i++) {
                out.add(channelValue(dmx, b.universe, b.channel + i));
            }
            o.add("dmx", out);
            if (width == 3) {
                o.addProperty("color", String.format("#%02x%02x%02x",
                        channelValue(dmx, b.universe, b.channel),
                        channelValue(dmx, b.universe, b.channel + 1),
                        channelValue(dmx, b.universe, b.channel + 2)));
            }
            arr.add(o);
        }
        return arr;
    }

    private static int channelValue(Map<Integer, byte[]> dmx, int universe, int channel) {
        byte[] buf = dmx.get(universe);
        if (buf == null || channel < 1 || channel > buf.length) {
            return 0;
        }
        return buf[channel - 1] & 0xFF;
    }

    // ---- persistence ---------------------------------------------------------

    public synchronized com.google.gson.JsonObject toJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("nextId", nextId);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (SoundBlock b : blocks.values()) {
            com.google.gson.JsonObject so = new com.google.gson.JsonObject();
            so.addProperty("id", b.id);
            so.addProperty("name", b.name);
            so.addProperty("mode", b.mode.name());
            so.addProperty("x", b.x);
            so.addProperty("y", b.y);
            so.addProperty("z", b.z);
            so.addProperty("radius", b.radius);
            so.addProperty("universe", b.universe);
            so.addProperty("channel", b.channel);
            so.addProperty("gain", b.gain);
            so.addProperty("decay", b.decay);
            so.addProperty("beatThreshold", b.beatThreshold);
            arr.add(so);
        }
        o.add("blocks", arr);
        return o;
    }

    public synchronized void load(com.google.gson.JsonObject o) {
        if (o == null) {
            return;
        }
        nextId = o.has("nextId") ? o.get("nextId").getAsInt() : 1;
        if (o.has("blocks")) {
            for (var el : o.getAsJsonArray("blocks")) {
                com.google.gson.JsonObject so = el.getAsJsonObject();
                SoundBlock b = new SoundBlock(
                        so.get("id").getAsInt(),
                        so.get("name").getAsString(),
                        Mode.valueOf(so.get("mode").getAsString()),
                        so.get("x").getAsInt(),
                        so.get("y").getAsInt(),
                        so.get("z").getAsInt());
                b.radius = so.has("radius") ? so.get("radius").getAsInt() : 8;
                b.universe = so.has("universe") ? so.get("universe").getAsInt() : 1;
                b.channel = so.has("channel") ? so.get("channel").getAsInt() : 1;
                b.gain = so.has("gain") ? so.get("gain").getAsDouble() : 1.0;
                b.decay = so.has("decay") ? so.get("decay").getAsDouble() : 0.15;
                b.beatThreshold = so.has("beatThreshold") ? so.get("beatThreshold").getAsDouble() : 1.6;
                blocks.put(b.id, b);
            }
        }
    }
}
