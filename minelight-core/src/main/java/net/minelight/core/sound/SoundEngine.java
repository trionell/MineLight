package net.minelight.core.sound;

import net.minelight.core.api.GameEvent;
import net.minelight.core.engine.ConsoleEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sound-to-light engine.
 *
 * <p>The console-agnostic half of MineLight's sound-reactive blocks. It does
 * not touch Minecraft audio itself — the Fabric bridge feeds it two kinds of
 * input:</p>
 *
 * <ul>
 *   <li><b>Discrete note events</b> from note blocks ({@link #onNote}) —
 *       pitch + instrument.</li>
 *   <li><b>Amplitude samples</b> from sound-meter / beat / spectrum blocks
 *       ({@link #onSample}) — a 0–1 level, optionally split into
 *       bass/mid/treble bands.</li>
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
        NOTE, LEVEL, BEAT, SPECTRUM
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
        emitSoundEvent("sound.level", b, value);
    }

    private void processBeat(SoundBlock b, double level) {
        double v = clamp01(level * b.gain);
        b.runningAvg = b.runningAvg == 0 ? v : b.runningAvg * 0.9 + v * 0.1;
        boolean onset = v > b.runningAvg * b.beatThreshold && v > 0.15;
        if (onset && !b.beatHigh) {
            b.beatHigh = true;
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
        emitSoundEvent("sound.spectrum", b, (r + g + bl) / 3);
    }

    /** Advance one game tick — decay envelopes for LEVEL blocks. */
    public void tick() {
        for (SoundBlock b : blocks.values()) {
            if (b.mode == Mode.LEVEL && b.envelope > 0) {
                b.envelope = Math.max(0, b.envelope - b.decay);
                engine.setDmx(b.universe, b.channel, (int) Math.round(b.envelope * 255));
            }
        }
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
