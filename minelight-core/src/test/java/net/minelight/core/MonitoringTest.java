package net.minelight.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minelight.core.api.FixtureBlock;
import net.minelight.core.api.GameEvent;
import net.minelight.core.api.PeerTracker;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.core.engine.EventLog;
import net.minelight.core.sound.SoundEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the state the WebConsole's monitor panels read: live block values,
 * sound-block telemetry, protocol-server status and the event log.
 */
class MonitoringTest {

    private static ConsoleEngine engine() throws Exception {
        return new ConsoleEngine(Files.createTempDirectory("minelight-monitor"));
    }

    /** Every port reads the same power, whichever face is asked for. */
    private static net.minelight.core.engine.FixtureBlockRegistry.PowerReader constant(int power) {
        return (x, y, z, side) -> power;
    }

    @Test
    void eventLogRetainsRecentEventsInOrder() {
        EventLog log = new EventLog(3);
        for (int i = 1; i <= 5; i++) {
            log.add(GameEvent.of("custom.e" + i));
        }
        assertEquals(5, log.lastSeq());
        var all = log.all();
        assertEquals(3, all.size(), "ring should drop the oldest beyond capacity");
        assertEquals("custom.e3", all.get(0).event().kind());
        assertEquals("custom.e5", all.get(2).event().kind());
    }

    @Test
    void eventLogTailsFromASequence() {
        EventLog log = new EventLog();
        log.add(GameEvent.of("a"));
        log.add(GameEvent.of("b"));
        long cursor = log.lastSeq();
        log.add(GameEvent.of("c"));

        var fresh = log.since(cursor);
        assertEquals(1, fresh.size());
        assertEquals("c", fresh.get(0).event().kind());
        assertEquals(1, log.toJson(cursor).size());
    }

    @Test
    void eventJsonCarriesKindAndData() {
        EventLog log = new EventLog();
        log.add(new GameEvent("block.dmx", Map.of("blockId", 7, "value", 255)));
        JsonObject o = log.all().get(0).toJson();
        assertEquals("block.dmx", o.get("kind").getAsString());
        assertEquals(7, o.getAsJsonObject("data").get("blockId").getAsInt());
        assertEquals(255, o.getAsJsonObject("data").get("value").getAsInt());
        assertTrue(o.get("at").getAsLong() > 0);
    }

    @Test
    void engineLogsWhatItEmits() throws Exception {
        ConsoleEngine engine = engine();
        engine.emit(GameEvent.of("custom.boom"));
        assertEquals(1, engine.eventLog().lastSeq());
        assertEquals("custom.boom", engine.eventLog().all().get(0).event().kind());
    }

    @Test
    void blockLiveJsonReportsInputAndOutput() throws Exception {
        ConsoleEngine engine = engine();
        FixtureBlock block = engine.blocks().add(FixtureBlock.Type.DIMMER, "Key", 1, 2, 3);
        block.setPort("in", FixtureBlock.PortMapping.channel("in", FixtureBlock.Side.ANY, 1, 5));

        engine.blocks().tick(constant(15));

        JsonArray blocks = engine.blocks().liveJson();
        assertEquals(1, blocks.size());
        JsonObject o = blocks.get(0).getAsJsonObject();
        assertEquals("Key", o.get("name").getAsString());
        assertEquals(1, o.get("x").getAsInt());

        JsonObject port = o.getAsJsonArray("ports").get(0).getAsJsonObject();
        assertEquals(15, port.get("raw").getAsInt(), "raw redstone strength");
        assertEquals(255, port.get("dmx").getAsInt(), "scaled DMX level");
        assertTrue(port.get("changedAt").getAsLong() > 0);
    }

    @Test
    void blockLiveJsonFollowsTheMergedBufferNotTheLastWrite() throws Exception {
        ConsoleEngine engine = engine();
        FixtureBlock block = engine.blocks().add(FixtureBlock.Type.DIMMER, "Key", 0, 0, 0);
        block.setPort("in", FixtureBlock.PortMapping.channel("in", FixtureBlock.Side.ANY, 1, 5));
        engine.blocks().tick(constant(0));

        // a console driving the same channel is what a monitor must show
        engine.setDmx(1, 5, 200);

        JsonObject port = engine.blocks().liveJson().get(0).getAsJsonObject()
                .getAsJsonArray("ports").get(0).getAsJsonObject();
        assertEquals(0, port.get("raw").getAsInt());
        assertEquals(200, port.get("dmx").getAsInt());
    }

    @Test
    void rgbBlockReportsResultingColour() throws Exception {
        ConsoleEngine engine = engine();
        FixtureBlock block = engine.blocks().add(FixtureBlock.Type.RGB, "Wash", 0, 0, 0);
        block.setPort("r", FixtureBlock.PortMapping.channel("r", FixtureBlock.Side.NORTH, 1, 1));
        block.setPort("g", FixtureBlock.PortMapping.channel("g", FixtureBlock.Side.EAST, 1, 2));
        block.setPort("b", FixtureBlock.PortMapping.channel("b", FixtureBlock.Side.WEST, 1, 3));
        engine.setDmx(1, 1, 0xFF);
        engine.setDmx(1, 2, 0x80);
        engine.setDmx(1, 3, 0x00);

        JsonObject o = engine.blocks().liveJson().get(0).getAsJsonObject();
        assertEquals("#ff8000", o.get("color").getAsString());
    }

    @Test
    void eventPortReportsFireCount() throws Exception {
        ConsoleEngine engine = engine();
        FixtureBlock block = engine.blocks().add(FixtureBlock.Type.EVENT, "Trigger", 0, 0, 0);
        block.setPort("in", FixtureBlock.PortMapping.event("in", FixtureBlock.Side.ANY, "custom.bang"));

        engine.blocks().tick(constant(0));
        engine.blocks().tick(constant(15)); // rising edge
        engine.blocks().tick(constant(15)); // held, no new fire

        JsonObject port = engine.blocks().liveJson().get(0).getAsJsonObject()
                .getAsJsonArray("ports").get(0).getAsJsonObject();
        assertEquals(1, port.get("fires").getAsLong());
        assertFalse(port.has("dmx"), "event ports have no DMX target");
    }

    @Test
    void removingABlockDropsItsPortState() throws Exception {
        ConsoleEngine engine = engine();
        FixtureBlock block = engine.blocks().add(FixtureBlock.Type.DIMMER, "Key", 0, 0, 0);
        engine.blocks().tick(constant(15));
        assertNotNull(engine.blocks().portState(block.id(), "in"));

        engine.blocks().remove(block.id());
        assertNull(engine.blocks().portState(block.id(), "in"));
        assertEquals(0, engine.blocks().liveJson().size());
    }

    @Test
    void soundLiveJsonReportsEnvelopeAndOutput() throws Exception {
        ConsoleEngine engine = engine();
        SoundEngine.SoundBlock b = engine.sound().add(SoundEngine.Mode.LEVEL, "Meter", 4, 5, 6);
        b.universe = 1;
        b.channel = 10;

        engine.sound().onSample(b.id, 1.0, 0, 0, 0);

        JsonObject o = engine.sound().liveJson().get(0).getAsJsonObject();
        assertEquals("LEVEL", o.get("mode").getAsString());
        assertEquals(4, o.get("x").getAsInt());
        assertEquals(1.0, o.get("level").getAsDouble(), 1e-9);
        assertEquals(1.0, o.get("envelope").getAsDouble(), 1e-9);
        assertEquals(255, o.getAsJsonArray("dmx").get(0).getAsInt());
    }

    @Test
    void spectrumBlockReportsThreeChannelsAndColour() throws Exception {
        ConsoleEngine engine = engine();
        SoundEngine.SoundBlock b = engine.sound().add(SoundEngine.Mode.SPECTRUM, "Bands", 0, 0, 0);
        b.channel = 1;

        engine.sound().onSample(b.id, 0, 1.0, 0.5, 0.0);

        JsonObject o = engine.sound().liveJson().get(0).getAsJsonObject();
        assertEquals(3, o.getAsJsonArray("dmx").size());
        assertEquals(255, o.getAsJsonArray("dmx").get(0).getAsInt());
        assertTrue(o.get("color").getAsString().startsWith("#ff"));
    }

    @Test
    void noteBlockReportsLastNote() throws Exception {
        ConsoleEngine engine = engine();
        SoundEngine.SoundBlock b = engine.sound().add(SoundEngine.Mode.NOTE, "Chimes", 0, 0, 0);

        engine.sound().onNote(1, 0, 0, 7, "harp");

        JsonObject o = engine.sound().liveJson().get(0).getAsJsonObject();
        assertEquals(7, o.get("lastNote").getAsInt());
        assertEquals("harp", o.get("lastInstrument").getAsString());
        assertTrue(o.get("lastActivity").getAsLong() > 0);
    }

    @Test
    void peerTrackerRecordsAndCountsTraffic() {
        PeerTracker peers = new PeerTracker();
        peers.seen("10.0.0.5:6454", "ArtDmx u1");
        peers.seen("10.0.0.5:6454", "ArtDmx u1");
        peers.seen("10.0.0.9:6454", "ArtPoll");

        assertEquals(2, peers.size());
        JsonArray arr = peers.toJson();
        assertEquals(2, arr.size());
        JsonObject first = arr.get(0).getAsJsonObject();
        assertTrue(first.has("address"));
        assertTrue(first.has("ageMs"));

        long total = 0;
        for (var el : arr) {
            total += el.getAsJsonObject().get("packets").getAsLong();
        }
        assertEquals(3, total);
    }

    @Test
    void protocolServerStatusCarriesTheBasics() throws Exception {
        ConsoleEngine engine = engine();
        var artnet = new net.minelight.core.artnet.ArtNetServer(engine);
        artnet.enableUniverse(1);
        artnet.enableUniverse(4);

        JsonObject status = artnet.status();
        assertEquals("Art-Net", status.get("name").getAsString());
        assertFalse(status.get("running").getAsBoolean());
        assertEquals(2, status.getAsJsonArray("outputUniverses").size());
        assertTrue(status.has("peers"));
    }
}
