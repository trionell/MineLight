package net.minelight.core.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minelight.core.api.GameEvent;
import net.minelight.core.api.ProtocolServer;
import net.minelight.core.engine.ConsoleEngine;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP REST API server.
 *
 * <p>Exposes the whole engine over JSON so command blocks, external scripts,
 * stream-deck plugins, home-automation (Home Assistant), and CI can drive
 * Minecraft lighting without any custom client:</p>
 *
 * <ul>
 *   <li>{@code GET  /api/patch} — current patch</li>
 *   <li>{@code POST /api/patch} — add fixture {name,mode,universe,address,x,y,z,kind}</li>
 *   <li>{@code DELETE /api/patch/{id}} — remove fixture</li>
 *   <li>{@code GET  /api/dmx} — merged DMX snapshot</li>
 *   <li>{@code POST /api/dmx} — set channel {universe,channel,value}</li>
 *   <li>{@code POST /api/fixture/{id}/intensity} — {value}</li>
 *   <li>{@code POST /api/fixture/{id}/set} — {levels:[...]}</li>
 *   <li>{@code GET  /api/presets} / {@code POST /api/presets/{name}/apply}</li>
 *   <li>{@code POST /api/cue/{list}/go} — {index?}</li>
 *   <li>{@code POST /api/event} — {kind, data:{...}} (command-block trigger)</li>
 *   <li>{@code GET  /api/redstone} — readback state</li>
 *   <li>{@code GET/POST /api/script} — Lua trigger script</li>
 * </ul>
 */
public final class HttpApiServer implements ProtocolServer {

    public static final int HTTP_PORT = 8080;

    private static final Gson GSON = new Gson();

    private final ConsoleEngine engine;
    private final int port;
    private HttpServer server;

    public HttpApiServer(ConsoleEngine engine) {
        this(engine, HTTP_PORT);
    }

    public HttpApiServer(ConsoleEngine engine, int port) {
        this.engine = engine;
        this.port = port;
    }

    @Override
    public String name() {
        return "HTTP API";
    }

    @Override
    public int defaultPort() {
        return HTTP_PORT;
    }

    @Override
    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "minelight-http");
            t.setDaemon(true);
            return t;
        }));

        server.createContext("/api/patch", this::handlePatch);
        server.createContext("/api/dmx", this::handleDmx);
        server.createContext("/api/fixture", this::handleFixture);
        server.createContext("/api/presets", this::handlePresets);
        server.createContext("/api/cue", this::handleCue);
        server.createContext("/api/event", this::handleEvent);
        server.createContext("/api/redstone", this::handleRedstone);
        server.createContext("/api/script", this::handleScript);
        server.createContext("/api/status", this::handleStatus);

        server.start();
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    // ---- helpers ---------------------------------------------------------

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange ex, int code, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange ex, int code, String msg) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("error", msg);
        sendJson(ex, code, o);
    }

    // ---- handlers --------------------------------------------------------

    private void handleStatus(HttpExchange ex) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("name", "MineLight");
        o.addProperty("version", "0.1.0");
        o.addProperty("fixtures", engine.patch().size());
        JsonArray servers = new JsonArray();
        for (ProtocolServer s : engine.servers()) {
            JsonObject so = new JsonObject();
            so.addProperty("name", s.name());
            so.addProperty("running", s.isRunning());
            servers.add(so);
        }
        o.add("protocols", servers);
        sendJson(ex, 200, o);
    }

    private void handlePatch(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equals(method)) {
            sendJson(ex, 200, engine.patch().toJson());
            return;
        }
        if ("POST".equals(method)) {
            JsonObject body = JsonParserCompat.parse(readBody(ex));
            int id = engine.patch().addFixture(
                    body.get("name").getAsString(),
                    modeOf(body.has("mode") ? body.get("mode").getAsString() : "Dimmer"),
                    body.has("universe") ? body.get("universe").getAsInt() : 1,
                    body.has("address") ? body.get("address").getAsInt() : 1,
                    body.has("x") ? body.get("x").getAsInt() : 0,
                    body.has("y") ? body.get("y").getAsInt() : 0,
                    body.has("z") ? body.get("z").getAsInt() : 0,
                    body.has("kind") ? body.get("kind").getAsString() : "lamp");
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            sendJson(ex, 201, o);
            return;
        }
        if ("DELETE".equals(method)) {
            String path = ex.getRequestURI().getPath();
            int id = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
            sendJson(ex, engine.patch().remove(id) ? 200 : 404, new JsonObject());
            return;
        }
        sendError(ex, 405, "Method not allowed");
    }

    private void handleDmx(HttpExchange ex) throws IOException {
        if ("GET".equals(ex.getRequestMethod())) {
            JsonObject o = new JsonObject();
            engine.dmxSnapshot().forEach((u, data) -> {
                JsonArray arr = new JsonArray();
                for (byte b : data) {
                    arr.add(b & 0xFF);
                }
                o.add(String.valueOf(u), arr);
            });
            sendJson(ex, 200, o);
            return;
        }
        if ("POST".equals(ex.getRequestMethod())) {
            JsonObject body = JsonParserCompat.parse(readBody(ex));
            engine.setDmx(body.get("universe").getAsInt(),
                    body.get("channel").getAsInt(),
                    body.get("value").getAsInt());
            sendJson(ex, 200, new JsonObject());
            return;
        }
        sendError(ex, 405, "Method not allowed");
    }

    private void handleFixture(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "Method not allowed");
            return;
        }
        // /api/fixture/<id>/<action>
        String[] parts = ex.getRequestURI().getPath().split("/");
        int id = Integer.parseInt(parts[3]);
        String action = parts[4];
        JsonObject body = JsonParserCompat.parse(readBody(ex));
        switch (action) {
            case "intensity" -> engine.setFixtureIntensity(id, body.get("value").getAsInt());
            case "set" -> {
                JsonArray levels = body.getAsJsonArray("levels");
                int[] arr = new int[levels.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = levels.get(i).getAsInt();
                }
                engine.setFixtureLevels(id, arr);
            }
            case "redstone" -> engine.setRedstone(id, body.get("on").getAsBoolean());
            default -> {
                sendError(ex, 404, "Unknown action " + action);
                return;
            }
        }
        sendJson(ex, 200, new JsonObject());
    }

    private void handlePresets(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if ("GET".equals(ex.getRequestMethod())) {
            JsonArray names = new JsonArray();
            engine.presets().keySet().forEach(names::add);
            sendJson(ex, 200, names);
            return;
        }
        if ("POST".equals(ex.getRequestMethod()) && path.endsWith("/apply")) {
            String name = path.split("/")[3];
            sendJson(ex, engine.applyPreset(name) ? 200 : 404, new JsonObject());
            return;
        }
        sendError(ex, 405, "Method not allowed");
    }

    private void handleCue(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "Method not allowed");
            return;
        }
        // /api/cue/<list>/go
        String[] parts = ex.getRequestURI().getPath().split("/");
        String list = parts[3];
        String body = readBody(ex);
        var cl = engine.cueList(list);
        ConsoleEngine.CueList.Cue cue;
        if (body.isBlank()) {
            cue = cl.next();
        } else {
            JsonObject o = JsonParserCompat.parse(body);
            cue = o.has("index") ? cl.go(o.get("index").getAsInt()) : cl.next();
        }
        if (cue != null && cue.levels() != null) {
            cue.levels().forEach(engine::setFixtureLevels);
        }
        JsonObject resp = new JsonObject();
        resp.addProperty("index", cl.index());
        sendJson(ex, 200, resp);
    }

    /** Command-block friendly: POST /api/event {"kind":"custom.explosion","data":{"power":4}} */
    private void handleEvent(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "Method not allowed");
            return;
        }
        JsonObject body = JsonParserCompat.parse(readBody(ex));
        String kind = body.get("kind").getAsString();
        Map<String, Object> data = new HashMap<>();
        if (body.has("data")) {
            body.getAsJsonObject("data").entrySet().forEach(e -> {
                var v = e.getValue();
                if (v.isJsonPrimitive()) {
                    var p = v.getAsJsonPrimitive();
                    if (p.isNumber()) {
                        data.put(e.getKey(), p.getAsNumber());
                    } else if (p.isBoolean()) {
                        data.put(e.getKey(), p.getAsBoolean());
                    } else {
                        data.put(e.getKey(), p.getAsString());
                    }
                }
            });
        }
        engine.emit(new GameEvent(kind, data));
        sendJson(ex, 202, new JsonObject());
    }

    private void handleRedstone(HttpExchange ex) throws IOException {
        JsonObject o = new JsonObject();
        engine.redstoneSnapshot().forEach((id, on) -> o.addProperty(String.valueOf(id), on));
        sendJson(ex, 200, o);
    }

    private void handleScript(HttpExchange ex) throws IOException {
        if ("GET".equals(ex.getRequestMethod())) {
            JsonObject o = new JsonObject();
            o.addProperty("script", engine.triggers().script());
            sendJson(ex, 200, o);
            return;
        }
        if ("POST".equals(ex.getRequestMethod())) {
            JsonObject body = JsonParserCompat.parse(readBody(ex));
            engine.triggers().setScript(body.get("script").getAsString());
            sendJson(ex, 200, new JsonObject());
            return;
        }
        sendError(ex, 405, "Method not allowed");
    }

    private static net.minelight.core.api.Patch.FixtureMode modeOf(String name) {
        return switch (name) {
            case "RGB" -> net.minelight.core.api.Patch.RGB;
            case "RGB+Dimmer" -> net.minelight.core.api.Patch.RGB_DIMMER;
            case "Strobe" -> net.minelight.core.api.Patch.STROBE;
            default -> net.minelight.core.api.Patch.DIMMER;
        };
    }

    /** Tiny indirection so we don't statically import JsonParser everywhere. */
    private static final class JsonParserCompat {
        static JsonObject parse(String s) {
            return com.google.gson.JsonParser.parseString(s).getAsJsonObject();
        }
    }
}
