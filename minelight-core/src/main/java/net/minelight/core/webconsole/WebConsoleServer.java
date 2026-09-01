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
 * DMX and redstone state over a WebSocket at {@code /ws} so the panel is
 * always in sync with the game and with any real console connected via
 * Art-Net / sACN.</p>
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
public final class WebConsoleServer implements ProtocolServer, ConsoleEngine.DmxListener {

    public static final int WEB_PORT = 8090;

    /** Telemetry push rate. Fast enough to read as live, slow enough to be cheap. */
    private static final int LIVE_HZ = 10;

    /** Device status changes on human timescales; polling it at LIVE_HZ is waste. */
    private static final long DEVICE_INTERVAL_MS = 1000;

    /** How long to trust a MIDI device enumeration before asking the OS again. */
    private static final long MIDI_CACHE_MS = 5000;

    private static final Gson GSON = new Gson();

    private final ConsoleEngine engine;
    private final int port;
    private final java.util.Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    private HttpServer server;
    private ScheduledExecutorService live;
    private List<String> midiInputs;
    private long midiInputsAt;
    private long lastDeviceBroadcast;
    /** Highest event sequence already pushed to clients. */
    private long sentSeq;

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
        server.createContext("/ws", this::handleWebSocket);
        server.start();
        engine.addDmxListener(this);

        sentSeq = engine.eventLog().lastSeq();
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
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return server != null;
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

    // ---- websocket --------------------------------------------------------

    private void handleWebSocket(HttpExchange ex) throws IOException {
        // Minimal RFC 6455 handshake (no external deps)
        String key = ex.getRequestHeaders().getFirst("Sec-WebSocket-Key");
        if (key == null) {
            ex.sendResponseHeaders(400, -1);
            return;
        }
        String accept = websocketAccept(key);
        ex.getResponseHeaders().set("Upgrade", "websocket");
        ex.getResponseHeaders().set("Connection", "Upgrade");
        ex.getResponseHeaders().set("Sec-WebSocket-Accept", accept);
        ex.sendResponseHeaders(101, -1);

        WebSocketSession session = new WebSocketSession(ex);
        sessions.add(session);
        // send initial state
        session.send(stateMessage());
        // read loop in a new thread; remove on close
        Thread t = new Thread(() -> {
            session.readLoop(this::onClientMessage);
            sessions.remove(session);
        }, "minelight-ws-client");
        t.setDaemon(true);
        t.start();
    }

    private static String websocketAccept(String key) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                    .getBytes(StandardCharsets.ISO_8859_1));
            return java.util.Base64.getEncoder().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject stateMessage() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "state");
        o.add("patch", engine.patch().toJson());
        JsonObject dmx = new JsonObject();
        engine.dmxSnapshot().forEach((u, data) -> {
            JsonArray arr = new JsonArray();
            for (byte b : data) {
                arr.add(b & 0xFF);
            }
            dmx.add(String.valueOf(u), arr);
        });
        o.add("dmx", dmx);
        JsonObject redstone = new JsonObject();
        engine.redstoneSnapshot().forEach((id, on) -> redstone.addProperty(String.valueOf(id), on));
        o.add("redstone", redstone);
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
     * Push telemetry that the DMX pump does not already carry: block and
     * sound-block runtime values, device status, and new events.
     */
    private void pushLive() {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            JsonObject o = new JsonObject();
            o.addProperty("type", "live");
            o.add("blocks", engine.blocks().liveJson());
            o.add("sound", engine.sound().liveJson());
            JsonObject redstone = new JsonObject();
            engine.redstoneSnapshot().forEach((id, on) -> redstone.addProperty(String.valueOf(id), on));
            o.add("redstone", redstone);

            long last = engine.eventLog().lastSeq();
            if (last > sentSeq) {
                List<EventLog.Entry> fresh = engine.eventLog().since(sentSeq);
                JsonArray events = new JsonArray();
                for (EventLog.Entry e : fresh) {
                    events.add(e.toJson());
                }
                o.add("events", events);
                sentSeq = last;
            }

            long now = System.currentTimeMillis();
            if (now - lastDeviceBroadcast >= DEVICE_INTERVAL_MS) {
                o.add("devices", devicesJson());
                lastDeviceBroadcast = now;
            }
            broadcast(o);
        } catch (Exception ignored) {
            // a monitor must never take the engine down with it
        }
    }

    private void onClientMessage(JsonObject msg) {
        String type = msg.get("type").getAsString();
        switch (type) {
            case "intensity" -> engine.setFixtureIntensity(
                    msg.get("fixtureId").getAsInt(), msg.get("value").getAsInt());
            case "set" -> {
                JsonArray levels = msg.getAsJsonArray("levels");
                int[] arr = new int[levels.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = levels.get(i).getAsInt();
                }
                engine.setFixtureLevels(msg.get("fixtureId").getAsInt(), arr);
            }
            case "preset" -> engine.applyPreset(msg.get("name").getAsString());
            case "cue" -> {
                var cl = engine.cueList(msg.get("list").getAsString());
                var cue = msg.has("index") ? cl.go(msg.get("index").getAsInt()) : cl.next();
                if (cue != null && cue.levels() != null) {
                    cue.levels().forEach(engine::setFixtureLevels);
                }
            }
            case "script" -> engine.triggers().setScript(msg.get("script").getAsString());
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
        for (WebSocketSession s : sessions) {
            s.send(json);
        }
    }

    @Override
    public void onDmx(Map<Integer, byte[]> universes) {
        if (sessions.isEmpty()) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "dmx");
        JsonObject dmx = new JsonObject();
        universes.forEach((u, data) -> {
            JsonArray arr = new JsonArray();
            for (byte b : data) {
                arr.add(b & 0xFF);
            }
            dmx.add(String.valueOf(u), arr);
        });
        o.add("dmx", dmx);
        broadcast(o);
    }

    // ---- minimal RFC6455 session -----------------------------------------

    private static final class WebSocketSession {
        private final HttpExchange ex;
        private final OutputStream out;
        private final InputStream in;

        WebSocketSession(HttpExchange ex) {
            this.ex = ex;
            this.out = ex.getResponseBody();
            this.in = ex.getRequestBody();
        }

        synchronized void send(JsonObject msg) {
            send(GSON.toJson(msg));
        }

        synchronized void send(String text) {
            try {
                byte[] payload = text.getBytes(StandardCharsets.UTF_8);
                java.io.ByteArrayOutputStream frame = new java.io.ByteArrayOutputStream();
                frame.write(0x81); // FIN + text
                if (payload.length < 126) {
                    frame.write(payload.length);
                } else if (payload.length < 65536) {
                    frame.write(126);
                    frame.write((payload.length >> 8) & 0xFF);
                    frame.write(payload.length & 0xFF);
                } else {
                    frame.write(127);
                    for (int i = 7; i >= 0; i--) {
                        frame.write((payload.length >> (8 * i)) & 0xFF);
                    }
                }
                frame.write(payload);
                out.write(frame.toByteArray());
                out.flush();
            } catch (IOException ignored) {
            }
        }

        void readLoop(java.util.function.Consumer<JsonObject> onMessage) {
            try {
                while (true) {
                    int b1 = in.read();
                    if (b1 < 0) {
                        break;
                    }
                    int b2 = in.read();
                    if (b2 < 0) {
                        break;
                    }
                    int opcode = b1 & 0x0F;
                    boolean masked = (b2 & 0x80) != 0;
                    long len = b2 & 0x7F;
                    if (len == 126) {
                        len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
                    } else if (len == 127) {
                        len = 0;
                        for (int i = 0; i < 8; i++) {
                            len = (len << 8) | (in.read() & 0xFF);
                        }
                    }
                    if (len > 1 << 20) { // 1 MiB cap
                        break;
                    }
                    byte[] mask = new byte[4];
                    if (masked) {
                        in.readNBytes(mask, 0, 4);
                    }
                    byte[] payload = in.readNBytes((int) len);
                    if (masked) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] ^= mask[i % 4];
                        }
                    }
                    if (opcode == 0x8) { // close
                        break;
                    }
                    if (opcode == 0x1) { // text
                        String s = new String(payload, StandardCharsets.UTF_8);
                        onMessage.accept(com.google.gson.JsonParser.parseString(s).getAsJsonObject());
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    ex.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
