package net.minelight.core;

import net.minelight.core.api.FixtureBlock;
import net.minelight.core.engine.ConsoleEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Events reaching a lighting desk as momentary DMX.
 *
 * <p>A console has no notion of a MineLight event; the only thing it can
 * trigger on is a channel going up and coming back down.</p>
 */
class PulseTest {

    private static ConsoleEngine engine() throws Exception {
        return new ConsoleEngine(Files.createTempDirectory("minelight-pulse"));
    }

    private static int chan(ConsoleEngine engine, int universe, int channel) {
        byte[] buf = engine.dmxSnapshot().get(universe);
        return buf == null ? 0 : buf[channel - 1] & 0xFF;
    }

    /** Wait for a channel to reach a value, or fail. */
    private static void awaitChannel(ConsoleEngine engine, int universe, int channel,
                                     int expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (chan(engine, universe, channel) == expected) {
                return;
            }
            Thread.sleep(5);
        }
        assertEquals(expected, chan(engine, universe, channel),
                "channel " + universe + "." + channel + " within " + timeoutMs + "ms");
    }

    @Test
    void aPulseGoesUpAndComesBackDown() throws Exception {
        ConsoleEngine engine = engine();
        engine.pulseDmx(1, 10, 255, 60);
        assertEquals(255, chan(engine, 1, 10), "up immediately");
        awaitChannel(engine, 1, 10, 0, 2000);
    }

    @Test
    void overlappingPulsesDoNotCutEachOtherShort() throws Exception {
        ConsoleEngine engine = engine();
        engine.pulseDmx(1, 10, 255, 80);
        Thread.sleep(60);
        engine.pulseDmx(1, 10, 255, 400); // re-fires while the first is still up

        // the first release lands about now and must not take the second down
        Thread.sleep(120);
        assertEquals(255, chan(engine, 1, 10), "the later pulse still owns the channel");
        awaitChannel(engine, 1, 10, 0, 2000);
    }

    @Test
    void pulsesOnOtherChannelsAreIndependent() throws Exception {
        ConsoleEngine engine = engine();
        engine.pulseDmx(1, 10, 255, 60);
        engine.pulseDmx(1, 11, 200, 5000);
        awaitChannel(engine, 1, 10, 0, 2000);
        assertEquals(200, chan(engine, 1, 11), "a different channel is untouched");
    }

    @Test
    void anOutOfRangeChannelIsIgnored() throws Exception {
        ConsoleEngine engine = engine();
        assertDoesNotThrow(() -> engine.pulseDmx(1, 0, 255, 10));
        assertDoesNotThrow(() -> engine.pulseDmx(1, 513, 255, 10));
    }

    @Test
    void anEventPortWithAChannelPulsesOnTheRisingEdge() throws Exception {
        ConsoleEngine engine = engine();
        FixtureBlock block = engine.blocks().add(FixtureBlock.Type.EVENT, "Boom", 0, 0, 0);
        block.setPort("in", new FixtureBlock.PortMapping("in", FixtureBlock.Side.ANY,
                FixtureBlock.Action.EMIT_EVENT, 1, 100, "custom.boom"));

        engine.blocks().tick((x, y, z, side) -> 0);
        assertEquals(0, chan(engine, 1, 100));

        engine.blocks().tick((x, y, z, side) -> 15);
        assertEquals(255, chan(engine, 1, 100), "rising edge pulses the channel");
        assertEquals("custom.boom",
                engine.eventLog().all().get(engine.eventLog().all().size() - 1).event().kind(),
                "and still emits the event");

        awaitChannel(engine, 1, 100, 0, 2000);
    }

    @Test
    void anEventPortWithNoChannelTouchesNoDmx() throws Exception {
        ConsoleEngine engine = engine();
        FixtureBlock block = engine.blocks().add(FixtureBlock.Type.EVENT, "Quiet", 0, 0, 0);
        block.setPort("in", FixtureBlock.PortMapping.event("in", FixtureBlock.Side.ANY, "custom.quiet"));

        engine.blocks().tick((x, y, z, side) -> 0);
        engine.blocks().tick((x, y, z, side) -> 15);

        assertTrue(engine.dmxSnapshot().isEmpty(), "channel 0 means events only");
    }

    @Test
    void luaCanPulseAChannelFromAnyGameEvent() throws Exception {
        ConsoleEngine engine = engine();
        engine.triggers().setScript("""
                minelight.on("player.death", function(e)
                  minelight.pulse(2, 50, 255, 60)
                end)
                """);

        engine.emit(net.minelight.core.api.GameEvent.of("player.death"));
        assertEquals(255, chan(engine, 2, 50));
        awaitChannel(engine, 2, 50, 0, 2000);
    }

    @Test
    void luaCanSetARawChannel() throws Exception {
        ConsoleEngine engine = engine();
        engine.triggers().setScript("""
                minelight.on("weather.thunder", function(e)
                  minelight.dmx(1, 7, 180)
                end)
                """);

        engine.emit(net.minelight.core.api.GameEvent.of("weather.thunder"));
        assertEquals(180, chan(engine, 1, 7), "dmx() holds, unlike pulse()");
    }
}
