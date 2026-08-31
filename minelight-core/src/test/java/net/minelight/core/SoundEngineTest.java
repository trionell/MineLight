package net.minelight.core;

import net.minelight.core.api.GameEvent;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.core.sound.SoundEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class SoundEngineTest {

    @Test
    void noteMapsPitchToHue() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-snd"));
        var block = engine.sound().add(SoundEngine.Mode.NOTE, "Keys", 0, 64, 0);
        block.channel = 10;

        engine.sound().onNote(0, 64, 0, 0, "harp"); // note 0 -> hue 0 -> red
        byte[] u1 = engine.dmxSnapshot().get(1);
        int r = u1[9] & 0xFF, g = u1[10] & 0xFF, b = u1[11] & 0xFF;
        assertTrue(r > 200 && g < 80 && b < 80, "note 0 should be red, got " + r + "," + g + "," + b);

        engine.sound().onNote(0, 64, 0, 6, "harp"); // tritone -> cyan-ish
        u1 = engine.dmxSnapshot().get(1);
        r = u1[9] & 0xFF; g = u1[10] & 0xFF; b = u1[11] & 0xFF;
        assertTrue(b > 150 && g > 150, "note 6 should be cyan-ish, got " + r + "," + g + "," + b);
    }

    @Test
    void noteOutOfRangeIgnored() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-snd"));
        var block = engine.sound().add(SoundEngine.Mode.NOTE, "Keys", 0, 64, 0);
        block.radius = 4;
        block.channel = 10;
        engine.sound().onNote(100, 64, 100, 0, "harp"); // far away
        assertNull(engine.dmxSnapshot().get(1)); // nothing written
    }

    @Test
    void levelFollowsAmplitudeWithEnvelope() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-snd"));
        var block = engine.sound().add(SoundEngine.Mode.LEVEL, "Meter", 0, 64, 0);
        block.channel = 20;
        block.decay = 0.25;

        engine.sound().onSample(block.id, 1.0, 0, 0, 0);
        assertEquals(255, engine.dmxSnapshot().get(1)[19] & 0xFF);

        // silence: envelope decays over ticks
        engine.sound().onSample(block.id, 0.0, 0, 0, 0);
        engine.sound().tick();
        int v = engine.dmxSnapshot().get(1)[19] & 0xFF;
        assertTrue(v < 255 && v > 0, "should decay, got " + v);
    }

    @Test
    void beatFiresOnOnsetOnly() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-snd"));
        var block = engine.sound().add(SoundEngine.Mode.BEAT, "Kick", 0, 64, 0);
        block.channel = 30;
        block.beatThreshold = 1.5;

        int[] beats = {0};
        engine.addEventListener(e -> {
            if ("sound.beat".equals(e.kind())) {
                beats[0]++;
            }
        });

        // build a baseline of low level
        for (int i = 0; i < 20; i++) {
            engine.sound().onSample(block.id, 0.1, 0, 0, 0);
        }
        // sudden hit
        engine.sound().onSample(block.id, 1.0, 0, 0, 0);
        assertEquals(1, beats[0]);
        assertEquals(255, engine.dmxSnapshot().get(1)[29] & 0xFF);

        // back to quiet, then another hit
        for (int i = 0; i < 20; i++) {
            engine.sound().onSample(block.id, 0.1, 0, 0, 0);
        }
        engine.sound().onSample(block.id, 1.0, 0, 0, 0);
        assertEquals(2, beats[0]);
    }

    @Test
    void spectrumSplitsBandsToRgb() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-snd"));
        var block = engine.sound().add(SoundEngine.Mode.SPECTRUM, "Spectrum", 0, 64, 0);
        block.channel = 40;

        engine.sound().onSample(block.id, 0, 1.0, 0.5, 0.0); // bass heavy
        byte[] u1 = engine.dmxSnapshot().get(1);
        assertEquals(255, u1[39] & 0xFF);        // R = bass
        assertEquals(127, u1[40] & 0xFF, 2);     // G = mid
        assertEquals(0, u1[41] & 0xFF);          // B = treble
    }

    @Test
    void soundBlocksPersist() throws Exception {
        var dir = Files.createTempDirectory("ml-snd");
        ConsoleEngine e1 = new ConsoleEngine(dir);
        var b = e1.sound().add(SoundEngine.Mode.SPECTRUM, "Spectrum", 3, 64, 3);
        b.channel = 50;
        b.gain = 2.0;
        e1.save();

        ConsoleEngine e2 = new ConsoleEngine(dir);
        e2.load();
        assertEquals(1, e2.sound().size());
        var loaded = e2.sound().byId(b.id);
        assertNotNull(loaded);
        assertEquals("Spectrum", loaded.name);
        assertEquals(50, loaded.channel);
        assertEquals(2.0, loaded.gain, 1e-9);
    }

    @Test
    void gainClipsToFullScale() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-snd"));
        var block = engine.sound().add(SoundEngine.Mode.LEVEL, "Hot", 0, 64, 0);
        block.channel = 60;
        block.gain = 5.0;
        engine.sound().onSample(block.id, 1.0, 0, 0, 0);
        assertEquals(255, engine.dmxSnapshot().get(1)[59] & 0xFF); // clipped, not overflowed
    }
}
