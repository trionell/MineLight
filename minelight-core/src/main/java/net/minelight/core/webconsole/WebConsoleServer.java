package net.minelight.core.webconsole;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minelight.core.api.ProtocolServer;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.core.engine.EventLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The built-in WebConsole — a zero-install lighting console in the browser.
 *
 * <p>Serves a single-page app at {@code http://<host>:8090/} with a channel
 * grid, preset buttons, cue-list GO, and a Lua script editor. Streams live
 * DMX and redstone state as Server-Sent Events at {@code /events} so the
 * panel is always in sync with the game and with any real console connected
 * via Art-Net / sACN; the panel posts commands back to {@code /command}.</p>
 *
 * <p>It is also the monitor: alongside the patch it streams every placed
 * fixture and sound block with its live values, the status of each protocol
 * server and the devices talking to it, and the engine's event log — the
 * feedback and input signals coming back from the world and from real
 * desks.</p>
 *
 * <p>Because the WebConsole speaks the same internal API as GrandMA or
 * Titan, anything you build in the browser translates directly to the real
 * console later.</p>
 */
public final class WebConsoleServer implements ProtocolServer {

    public static final int WEB_PORT = 8090;

    /** Telemetry push rate. Fast enough to read as live, slow enough to be cheap. */
    private static final int LIVE_HZ = 10;

    /** Device status changes on human timescales; polling it at LIVE_HZ is waste. */
    private static final long DEVICE_INTERVAL_MS = 1000;

    /** How long to trust a MIDI device enumeration before asking the OS again. */
    private static final long MIDI_CACHE_MS = 5000;

    /** Idle gap after which a session is proven alive with an SSE comment. */
    private static final long KEEPALIVE_MS = 15_000;

    private static final Gson GSON = new Gson();

    private final ConsoleEngine engine;
    private final int port;
    private final java.util.Set<StreamSession> sessions = ConcurrentHashMap.newKeySet();

    private HttpServer server;
    private ScheduledExecutorService live;
    private List<String> midiInputs;
    private long midiInputsAt;
    private long lastDeviceBroadcast;
    /**
     * Last JSON broadcast per section, so unchanged sections are not resent.
     *
     * <p>Deliberately not primed when a client connects. A new client's state
     * message is current by construction, so at worst it receives one
     * redundant update; priming from it would instead convince the pump that
     * a change already went out and leave every older client stale.</p>
     */
    private final Map<String, String> lastSent = new ConcurrentHashMap<>();

    public WebConsoleServer(ConsoleEngine engine) {
        this(engine, WEB_PORT);
    }

    public WebConsoleServer(ConsoleEngine engine, int port) {
        this.engine = engine;
        this.port = port;
    }

    @Override
    public String name() {
        return "WebConsole";
    }

    @Override
    public int defaultPort() {
        return WEB_PORT;
    }

    @Override
    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "minelight-webconsole");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handleStatic);
        server.createContext("/events", this::handleEvents);
        server.createContext("/command", this::handleCommand);
        server.start();

        live = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "minelight-webconsole-live");
            t.setDaemon(true);
            return t;
        });
        live.scheduleAtFixedRate(this::pushLive, 0, 1000 / LIVE_HZ, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (live != null) {
            live.shutdownNow();
            live = null;
        }
        for (StreamSession s : sessions) {
            s.close();
        }
        sessions.clear();
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    @Override
    public JsonObject status() {
        JsonObject o = ProtocolServer.super.status();
        o.addProperty("port", port);
        JsonArray clients = new JsonArray();
        for (StreamSession s : sessions) {
            JsonObject c = new JsonObject();
            c.addProperty("address", s.address());
            c.addProperty("detail", "watching");
            clients.add(c);
        }
        o.add("peers", clients);
        return o;
    }

    // ---- static files -----------------------------------------------------

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if ("/".equals(path)) {
            path = "/index.html";
        }
        String resource = "/net/minelight/core/webconsole" + path;
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = in.readAllBytes();
            String contentType = path.endsWith(".html") ? "text/html; charset=utf-8"
                    : path.endsWith(".js") ? "application/javascript; charset=utf-8"
                    : path.endsWith(".css") ? "text/css; charset=utf-8"
                    : "application/octet-stream";
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ---- event stream -----------------------------------------------------

    /**
     * The live feed, as Server-Sent Events.
     *
     * <p>Not a WebSocket: {@code com.sun.net.httpserver} discards the body of
     * a 101 response, so an upgraded connection can complete its handshake and
     * then never deliver a byte. SSE is one-way, which is all the feed needs,
     * and it survives the reverse proxies people put in front of a game
     * server. Commands travel the other way over {@code POST /command}.</p>
     *
     * <p>The handler thread parks for the life of the connection — the
     * exchange is closed as soon as it returns — and wakes when a write fails
     * or the server shuts down.</p>
     */
    private void handleEvents(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);

        StreamSession session = new StreamSession(ex);
        try {
            // Take the cursor from the state message this client is about to
            // receive, so its first live push starts after the history it
            // already has rather than replaying it.
            JsonObject state = stateMessage();
            session.sentSeq = state.get("lastSeq").getAsLong();
            sessions.add(session);
            session.send(state);
            session.await();
        } finally {
            sessions.remove(session);
            session.close();
        }
    }

    private void handleCommand(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            onClientMessage(com.google.gson.JsonParser.parseString(body).getAsJsonObject());
            ex.sendResponseHeaders(204, -1);
        } catch (Exception e) {
            byte[] msg = ("{\"error\":\"" + e.getClass().getSimpleName() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(400, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
        } finally {
            ex.close();
        }
    }

    private JsonObject stateMessage() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "state");
        o.add("patch", engine.patch().toJson());
        o.add("dmx", dmxJson());
        o.add("redstone", redstoneJson());
        JsonArray presets = new JsonArray();
        engine.presets().keySet().forEach(presets::add);
        o.add("presets", presets);
        o.addProperty("script", engine.triggers().script());
        o.add("blocks", engine.blocks().liveJson());
        o.add("sound", engine.sound().liveJson());
        o.add("devices", devicesJson());
        // A fresh client gets the whole retained ring so the signal feed is
        // populated the moment it connects, not only once something happens.
        o.add("events", engine.eventLog().toJson(0));
        o.addProperty("lastSeq", engine.eventLog().lastSeq());
        return o;
    }

    private JsonObject dmxJson() {
        JsonObject dmx = new JsonObject();
        engine.dmxSnapshot().forEach((u, data) -> {
            JsonArray arr = new JsonArray();
            for (byte b : data) {
                arr.add(b & 0xFF);
            }
            dmx.add(String.valueOf(u), arr);
        });
        return dmx;
    }

    private JsonArray devicesJson() {
        JsonArray arr = new JsonArray();
        for (ProtocolServer s : engine.servers()) {
            arr.add(s.status());
        }
        arr.add(midiStatus());
        return arr;
    }

    /**
     * MIDI inputs the JVM can see.
     *
     * <p>MineLight does not open them itself — {@code MidiService} is opt-in —
     * but a monitor asking "what devices are there?" still wants to know a
     * controller is plugged in, so they are reported as available rather than
     * connected.</p>
     */
    private JsonObject midiStatus() {
        JsonObject o = new JsonObject();
        o.addProperty("name", "MIDI");
        o.addProperty("running", false);
        JsonArray peers = new JsonArray();
        for (String input : cachedMidiInputs()) {
            JsonObject p = new JsonObject();
            p.addProperty("address", input);
            p.addProperty("detail", "available");
            peers.add(p);
        }
        o.add("peers", peers);
        return o;
    }

    /** Enumerating MIDI devices hits the OS, so keep the answer for a while. */
    private synchronized List<String> cachedMidiInputs() {
        long now = System.currentTimeMillis();
        if (midiInputs == null || now - midiInputsAt > MIDI_CACHE_MS) {
            try {
                midiInputs = net.minelight.core.midi.MidiService.availableInputs();
            } catch (Throwable t) {
                // headless servers and locked-down JVMs have no MIDI subsystem
                midiInputs = List.of();
            }
            midiInputsAt = now;
        }
        return midiInputs;
    }

    /**
     * Push whatever has changed since the last tick.
     *
     * <p>Sections are compared against what was last broadcast and omitted
     * when identical, so an idle show sends nothing at all. This is not just
     * bandwidth: the panel rebuilds a DOM subtree for every section it
     * receives, and a page that mutates ten times a second keeps every
     * MutationObserver in the browser — including the ones browser extensions
     * install on each page — busy scanning it. That was costing far more CPU
     * than the console itself.</p>
     */
    private void pushLive() {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            JsonObject o = new JsonObject();
            o.addProperty("type", "live");
            boolean any = false;
            any |= addIfChanged(o, "dmx", dmxJson());
            any |= addIfChanged(o, "blocks", engine.blocks().liveJson());
            any |= addIfChanged(o, "sound", engine.sound().liveJson());
            any |= addIfChanged(o, "redstone", redstoneJson());

            long now = System.currentTimeMillis();
            if (now - lastDeviceBroadcast >= DEVICE_INTERVAL_MS) {
                lastDeviceBroadcast = now;
                any |= addIfChanged(o, "devices", devicesJson());
            }

            // Each client is at its own point in the event log — one that
            // connected a moment ago must not be sent the backlog its state
            // message already carried — so events are appended per session.
            long last = engine.eventLog().lastSeq();
            String shared = any ? GSON.toJson(o) : null;
            for (StreamSession s : sessions) {
                if (last > s.sentSeq) {
                    JsonObject withEvents = o.deepCopy();
                    JsonArray events = new JsonArray();
                    for (EventLog.Entry e : engine.eventLog().since(s.sentSeq)) {
                        events.add(e.toJson());
                    }
                    withEvents.add("events", events);
                    s.sentSeq = last;
                    s.send(GSON.toJson(withEvents));
                } else if (shared != null) {
                    s.send(shared);
                } else if (now - s.lastWrite >= KEEPALIVE_MS) {
                    // Nothing to say, but a silent socket is indistinguishable
                    // from a dead one. A comment keeps the connection proven
                    // without waking the page's onmessage handler.
                    s.comment();
                }
            }
        } catch (Exception ignored) {
            // a monitor must never take the engine down with it
        }
    }

    /**
     * Add a section only when it differs from the last one broadcast.
     *
     * @return whether the section was added
     */
    private boolean addIfChanged(JsonObject target, String key, com.google.gson.JsonElement value) {
        String json = GSON.toJson(value);
        if (json.equals(lastSent.get(key))) {
            return false;
        }
        lastSent.put(key, json);
        target.add(key, value);
        return true;
    }

    private JsonObject redstoneJson() {
        JsonObject redstone = new JsonObject();
        engine.redstoneSnapshot().forEach((id, on) -> redstone.addProperty(String.valueOf(id), on));
        return redstone;
    }

    private void onClientMessage(JsonObject msg) {
        String type = msg.get("type").getAsString();
        switch (type) {
            case "intensity" -> {
                int id = msg.get("fixtureId").getAsInt();
                int value = msg.get("value").getAsInt();
                engine.setFixtureIntensity(id, value);
                engine.emitControl("web", "fixture",
                        Map.of("fixtureId", id, "action", "intensity", "value", value));
            }
            case "set" -> {
                JsonArray levels = msg.getAsJsonArray("levels");
                int[] arr = new int[levels.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = levels.get(i).getAsInt();
                }
                int id = msg.get("fixtureId").getAsInt();
                engine.setFixtureLevels(id, arr);
                engine.emitControl("web", "fixture",
                        Map.of("fixtureId", id, "action", "set",
                                "levels", java.util.Arrays.toString(arr)));
            }
            case "preset" -> {
                String name = msg.get("name").getAsString();
                engine.applyPreset(name);
                engine.emitControl("web", "preset", Map.of("name", name));
            }
            case "cue" -> {
                String list = msg.get("list").getAsString();
                var cl = engine.cueList(list);
                var cue = msg.has("index") ? cl.go(msg.get("index").getAsInt()) : cl.next();
                if (cue != null && cue.levels() != null) {
                    cue.levels().forEach(engine::setFixtureLevels);
                }
                engine.emitControl("web", "cue", Map.of("list", list, "index", cl.index()));
            }
            case "script" -> {
                engine.triggers().setScript(msg.get("script").getAsString());
                engine.emitControl("web", "script", Map.of());
            }
            case "event" -> {
                Map<String, Object> data = new java.util.HashMap<>();
                if (msg.has("data")) {
                    msg.getAsJsonObject("data").entrySet().forEach(e ->
                            data.put(e.getKey(), e.getValue().getAsString()));
                }
                engine.emit(new net.minelight.core.api.GameEvent(
                        msg.get("kind").getAsString(), data));
            }
            case "savePreset" -> {
                Map<Integer, int[]> levels = new java.util.HashMap<>();
                msg.getAsJsonObject("levels").entrySet().forEach(e -> {
                    JsonArray arr = e.getValue().getAsJsonArray();
                    int[] l = new int[arr.size()];
                    for (int i = 0; i < l.length; i++) {
                        l[i] = arr.get(i).getAsInt();
                    }
                    levels.put(Integer.parseInt(e.getKey()), l);
                });
                engine.savePreset(msg.get("name").getAsString(), levels);
            }
            case "addFixture" -> {
                engine.patch().addFixture(
                        msg.get("name").getAsString(),
                        modeOf(msg.has("mode") ? msg.get("mode").getAsString() : "Dimmer"),
                        msg.has("universe") ? msg.get("universe").getAsInt() : 1,
                        msg.has("address") ? msg.get("address").getAsInt() : 1,
                        msg.has("x") ? msg.get("x").getAsInt() : 0,
                        msg.has("y") ? msg.get("y").getAsInt() : 0,
                        msg.has("z") ? msg.get("z").getAsInt() : 0,
                        msg.has("kind") ? msg.get("kind").getAsString() : "lamp");
                broadcast(stateMessage());
            }
            default -> {
            }
        }
    }

    private static net.minelight.core.api.Patch.FixtureMode modeOf(String name) {
        return switch (name) {
            case "RGB" -> net.minelight.core.api.Patch.RGB;
            case "RGB+Dimmer" -> net.minelight.core.api.Patch.RGB_DIMMER;
            case "Strobe" -> net.minelight.core.api.Patch.STROBE;
            default -> net.minelight.core.api.Patch.DIMMER;
        };
    }

    private void broadcast(JsonObject msg) {
        String json = GSON.toJson(msg);
        for (StreamSession s : sessions) {
            s.send(json);
        }
    }

    // ---- one connected browser --------------------------------------------

    private static final class StreamSession {
        private final HttpExchange ex;
        private final OutputStream out;
        /** Highest event sequence this client has been sent. */
        volatile long sentSeq;
        /** When anything was last written, for keepalives. */
        volatile long lastWrite = System.currentTimeMillis();
        private final java.util.concurrent.CountDownLatch closed =
                new java.util.concurrent.CountDownLatch(1);

        StreamSession(HttpExchange ex) {
            this.ex = ex;
            this.out = ex.getResponseBody();
        }

        String address() {
            var remote = ex.getRemoteAddress();
            return remote == null ? "?" : remote.getAddress().getHostAddress();
        }

        void send(JsonObject msg) {
            send(GSON.toJson(msg));
        }

        synchronized void send(String json) {
            if (closed.getCount() == 0) {
                return;
            }
            try {
                // SSE frames are newline-delimited, so the payload must not
                // contain a raw newline. Gson emits none, but a pretty-printed
                // or hand-built string would.
                out.write(("data: " + json.replace("\n", " ") + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                lastWrite = System.currentTimeMillis();
            } catch (IOException e) {
                close();
            }
        }

        /** An SSE comment: proves the socket without delivering an event. */
        synchronized void comment() {
            if (closed.getCount() == 0) {
                return;
            }
            try {
                out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                lastWrite = System.currentTimeMillis();
            } catch (IOException e) {
                close();
            }
        }

        /** Park until the client goes away or the server stops. */
        void await() {
            try {
                closed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void close() {
            closed.countDown();
            try {
                ex.close();
            } catch (Exception ignored) {
            }
        }
    }
}
