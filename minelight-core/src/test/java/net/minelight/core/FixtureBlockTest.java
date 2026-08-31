package net.minelight.core;

import net.minelight.core.api.FixtureBlock;
import net.minelight.core.api.GameEvent;
import net.minelight.core.engine.ConsoleEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class FixtureBlockTest {

    /** Helper: a power reader backed by a map of "x,y,z,side" -> power. */
    private static FixtureBlockRegistryPower power(Map<String, Integer> map) {
        return new FixtureBlockRegistryPower(map);
    }

    // tiny adapter so tests don't depend on the registry's functional interface shape
    private static final class FixtureBlockRegistryPower
            implements net.minelight.core.engine.FixtureBlockRegistry.PowerReader {
        private final Map<String, Integer> map;

        FixtureBlockRegistryPower(Map<String, Integer> map) {
            this.map = map;
        }

        @Override
        public int read(int x, int y, int z, FixtureBlock.Side side) {
            Integer any = map.get(x + "," + y + "," + z + ",ANY");
            if (any != null) {
                return any;
            }
            return map.getOrDefault(x + "," + y + "," + z + "," + side.name(), 0);
        }
    }

    @Test
    void dimmerScalesRedstoneToDmx() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-test"));
        var block = engine.blocks().add(FixtureBlock.Type.DIMMER, "Dimmer", 0, 64, 0);
        int channel = block.ports().get(0).channel();

        Map<String, Integer> p = new ConcurrentHashMap<>();
        p.put("0,64,0,ANY", 15);
        engine.blocks().tick(power(p));

        assertEquals(255, engine.dmxSnapshot().get(1)[channel - 1] & 0xFF);

        p.put("0,64,0,ANY", 7);
        engine.blocks().tick(power(p));
        assertEquals(7 * 255 / 15, engine.dmxSnapshot().get(1)[channel - 1] & 0xFF);
    }

    @Test
    void rgbReadsThreeSides() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-test"));
        var block = engine.blocks().add(FixtureBlock.Type.RGB, "RGB", 10, 64, 10);

        int rCh = -1, gCh = -1, bCh = -1;
        for (var port : block.ports()) {
            switch (port.name()) {
                case "r" -> rCh = port.channel();
                case "g" -> gCh = port.channel();
                case "b" -> bCh = port.channel();
            }
        }

        Map<String, Integer> p = new ConcurrentHashMap<>();
        p.put("10,64,10,NORTH", 15); // R full
        p.put("10,64,10,EAST", 8);   // G half
        p.put("10,64,10,WEST", 0);   // B off
        engine.blocks().tick(power(p));

        byte[] u1 = engine.dmxSnapshot().get(1);
        assertEquals(255, u1[rCh - 1] & 0xFF);
        assertEquals(8 * 255 / 15, u1[gCh - 1] & 0xFF);
        assertEquals(0, u1[bCh - 1] & 0xFF);
    }

    @Test
    void eventBlockFiresOnRisingEdgeOnly() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-test"));
        var block = engine.blocks().add(FixtureBlock.Type.EVENT, "Button", 0, 64, 0);
        String eventKind = block.ports().get(0).event();

        int[] fired = {0};
        engine.addEventListener(e -> {
            if (eventKind.equals(e.kind())) {
                fired[0]++;
            }
        });

        Map<String, Integer> p = new ConcurrentHashMap<>();
        // rising edge
        p.put("0,64,0,ANY", 15);
        engine.blocks().tick(power(p));
        assertEquals(1, fired[0]);
        // held high — no re-fire
        engine.blocks().tick(power(p));
        assertEquals(1, fired[0]);
        // release then press again
        p.put("0,64,0,ANY", 0);
        engine.blocks().tick(power(p));
        p.put("0,64,0,ANY", 15);
        engine.blocks().tick(power(p));
        assertEquals(2, fired[0]);
    }

    @Test
    void feedbackBlockReportsConsoleLevel() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-test"));
        var block = engine.blocks().add(FixtureBlock.Type.FEEDBACK, "Monitor", 0, 64, 0);
        int channel = block.ports().get(0).channel();

        // console drives the channel
        engine.setDmx(1, channel, 255);
        engine.blocks().tick(power(Map.of()));
        assertEquals(15, engine.blocks().feedbackLevel(block.id()));

        engine.setDmx(1, channel, 0);
        engine.blocks().tick(power(Map.of()));
        assertEquals(0, engine.blocks().feedbackLevel(block.id()));
    }

    @Test
    void registryPersists() throws Exception {
        var dir = Files.createTempDirectory("ml-test");
        ConsoleEngine e1 = new ConsoleEngine(dir);
        var b = e1.blocks().add(FixtureBlock.Type.DIMMER, "Lamp", 5, 64, 5);
        b.setPort("in", FixtureBlock.PortMapping.channel("in", FixtureBlock.Side.UP, 2, 42));
        e1.blocks().add(b); // re-register
        e1.save();

        ConsoleEngine e2 = new ConsoleEngine(dir);
        e2.load();
        assertEquals(1, e2.blocks().size());
        var loaded = e2.blocks().at(5, 64, 5);
        assertNotNull(loaded);
        assertEquals("Lamp", loaded.name());
        assertEquals(42, loaded.ports().get(0).channel());
        assertEquals(FixtureBlock.Side.UP, loaded.ports().get(0).side());
    }

    @Test
    void gameEventsCarryBlockContext() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("ml-test"));
        engine.blocks().add(FixtureBlock.Type.DIMMER, "Kitchen", 7, 64, 7);

        GameEvent[] seen = {null};
        engine.addEventListener(e -> {
            if ("block.dmx".equals(e.kind())) {
                seen[0] = e;
            }
        });

        Map<String, Integer> p = new ConcurrentHashMap<>();
        p.put("7,64,7,ANY", 15);
        engine.blocks().tick(power(p));

        assertNotNull(seen[0]);
        assertEquals("Kitchen", seen[0].getString("blockName", ""));
        assertEquals(15, seen[0].getInt("raw", -1));
        assertEquals(255, seen[0].getInt("value", -1));
    }
}
