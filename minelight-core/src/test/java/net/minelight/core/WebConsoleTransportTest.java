package net.minelight.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minelight.core.api.FixtureBlock;
import net.minelight.core.api.GameEvent;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.core.webconsole.WebConsoleServer;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end cover for the WebConsole's live feed.
 *
 * <p>The console previously spoke WebSocket, which never delivered a single
 * frame: {@code com.sun.net.httpserver} completes the 101 handshake and then
 * discards the response body. The failure was invisible — the browser showed
 * a connected socket and an empty page — so the transport is tested over a
 * real socket rather than by calling the handlers directly.</p>
 */
class WebConsoleTransportTest {

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static JsonObject readEvent(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("data: ")) {
                return JsonParser.parseString(line.substring(6)).getAsJsonObject();
            }
        }
        throw new EOFException("stream ended before an event arrived");
    }

    @Test
    void streamsStateThenLiveUpdatesAndAcceptsCommands() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-ws"));
        int port = freePort();
        WebConsoleServer web = new WebConsoleServer(engine, port);
        engine.registerServer(web);
        engine.start();
        try {
            engine.patch().addFixture("House", net.minelight.core.api.Patch.DIMMER,
                    1, 1, 0, 64, 0, "lamp");
            FixtureBlock block = engine.blocks().add(FixtureBlock.Type.DIMMER, "Key", 3, 64, 5);
            block.setPort("in", FixtureBlock.PortMapping.channel("in", FixtureBlock.Side.ANY, 1, 9));
            engine.blocks().tick((x, y, z, side) -> 15);

            HttpURLConnection conn = (HttpURLConnection) URI
                    .create("http://127.0.0.1:" + port + "/events").toURL().openConnection();
            conn.setReadTimeout(10_000);
            assertTrue(conn.getHeaderField("Content-Type").startsWith("text/event-stream"));

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                JsonObject state = readEvent(r);
                assertEquals("state", state.get("type").getAsString());
                assertEquals(1, state.getAsJsonArray("blocks").size(), "placed blocks reach the console");
                assertEquals(255, state.getAsJsonArray("blocks").get(0).getAsJsonObject()
                        .getAsJsonArray("ports").get(0).getAsJsonObject().get("dmx").getAsInt());
                assertTrue(state.getAsJsonArray("devices").size() > 0);
                long stateSeq = state.get("lastSeq").getAsLong();
                assertTrue(stateSeq > 0, "the block tick should already have been logged");

                // a command travels back the other way
                HttpURLConnection cmd = (HttpURLConnection) URI
                        .create("http://127.0.0.1:" + port + "/command").toURL().openConnection();
                cmd.setRequestMethod("POST");
                cmd.setDoOutput(true);
                cmd.getOutputStream().write(
                        "{\"type\":\"intensity\",\"fixtureId\":1,\"value\":222}"
                                .getBytes(StandardCharsets.UTF_8));
                assertEquals(204, cmd.getResponseCode());
                assertEquals(222, engine.dmxSnapshot().get(1)[0] & 0xFF);

                engine.emit(GameEvent.of("custom.probe", "n", 1));

                JsonObject live = null;
                for (int i = 0; i < 60 && live == null; i++) {
                    JsonObject msg = readEvent(r);
                    if ("live".equals(msg.get("type").getAsString()) && msg.has("events")) {
                        live = msg;
                    }
                }
                assertNotNull(live, "live telemetry should arrive on the stream");
                assertEquals(1, live.getAsJsonArray("events").size(),
                        "only events newer than the state message");
                assertEquals("custom.probe", live.getAsJsonArray("events").get(0)
                        .getAsJsonObject().get("kind").getAsString());
                assertEquals(222, live.getAsJsonObject("dmx").getAsJsonArray("1")
                        .get(0).getAsInt(), "live push carries DMX");
            }
        } finally {
            engine.stop();
        }
    }

    @Test
    void rejectsAMalformedCommand() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-ws"));
        int port = freePort();
        engine.registerServer(new WebConsoleServer(engine, port));
        engine.start();
        try {
            HttpURLConnection cmd = (HttpURLConnection) URI
                    .create("http://127.0.0.1:" + port + "/command").toURL().openConnection();
            cmd.setRequestMethod("POST");
            cmd.setDoOutput(true);
            cmd.getOutputStream().write("not json".getBytes(StandardCharsets.UTF_8));
            assertEquals(400, cmd.getResponseCode());
        } finally {
            engine.stop();
        }
    }
}
