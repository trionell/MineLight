package net.minelight.core;

import net.minelight.core.api.GameEvent;
import net.minelight.core.api.Patch;
import net.minelight.core.engine.ConsoleEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {

    @Test
    void patchAddAndSerialize() {
        Patch p = new Patch();
        int id = p.addFixture("Stage Left", Patch.DIMMER, 1, 1, 10, 64, 10, "lamp");
        assertEquals(1, id);
        assertEquals(1, p.size());
        Patch round = Patch.fromJson(p.toJson());
        assertEquals(1, round.size());
        assertEquals("Stage Left", round.fixtures().get(0).name());
    }

    @Test
    void engineDmxMerge() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-test"));
        int id = engine.patch().addFixture("Lamp", Patch.DIMMER, 1, 1, 0, 64, 0, "lamp");
        engine.setFixtureIntensity(id, 200);
        byte[] u1 = engine.dmxSnapshot().get(1);
        assertNotNull(u1);
        assertEquals(200, u1[0] & 0xFF);
    }

    @Test
    void presetsApply() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-test"));
        int id = engine.patch().addFixture("Lamp", Patch.DIMMER, 1, 1, 0, 64, 0, "lamp");
        engine.savePreset("full", Map.of(id, new int[]{255}));
        assertTrue(engine.applyPreset("full"));
        assertEquals(255, engine.dmxSnapshot().get(1)[0] & 0xFF);
        assertFalse(engine.applyPreset("nope"));
    }

    @Test
    void cueListAdvances() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-test"));
        int id = engine.patch().addFixture("Lamp", Patch.DIMMER, 1, 1, 0, 64, 0, "lamp");
        var cl = engine.cueList("main");
        cl.cues.add(new ConsoleEngine.CueList.Cue("blackout", Map.of(id, new int[]{0}), 0));
        cl.cues.add(new ConsoleEngine.CueList.Cue("full", Map.of(id, new int[]{255}), 1));
        // first go() jumps to index 0
        var cue = cl.go(0);
        assertNotNull(cue);
        cue.levels().forEach(engine::setFixtureLevels);
        assertEquals(0, engine.dmxSnapshot().get(1)[0] & 0xFF);
        // then next() advances
        cue = cl.next();
        cue.levels().forEach(engine::setFixtureLevels);
        assertEquals(255, engine.dmxSnapshot().get(1)[0] & 0xFF);
    }

    @Test
    void redstoneEventsFire() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-test"));
        int id = engine.patch().addFixture("Lamp", Patch.DIMMER, 1, 1, 0, 64, 0, "lamp");
        boolean[] fired = {false};
        engine.addEventListener(e -> {
            if (GameEvent.REDSTONE_ON.equals(e.kind()) && id == e.getInt("fixtureId", -1)) {
                fired[0] = true;
            }
        });
        engine.setRedstone(id, true);
        assertTrue(fired[0]);
        assertTrue(engine.redstone(id));
    }

    @Test
    void luaTriggerRuns() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-test"));
        int id = engine.patch().addFixture("Lamp", Patch.DIMMER, 1, 1, 0, 64, 0, "lamp");
        engine.triggers().setScript(
                "minelight.on('custom.bump', function(e) minelight.intensity(" + id + ", 128) end)");
        engine.emit(GameEvent.of("custom.bump"));
        // Lua runs synchronously on emit
        assertEquals(128, engine.dmxSnapshot().get(1)[0] & 0xFF);
    }
}
