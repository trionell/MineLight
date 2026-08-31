package net.minelight.core.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minelight.core.api.ConsoleEngineContext;
import net.minelight.core.api.GameEvent;
import net.minelight.core.api.Patch;
import net.minelight.core.api.ProtocolServer;
import net.minelight.core.scripting.TriggerEngine;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The central hub of MineLight.
 *
 * <p>The engine owns:</p>
 * <ul>
 *   <li>The {@link Patch} (fixtures in the world)</li>
 *   <li>The merged DMX output buffer (per-universe, 512 channels)</li>
 *   <li>The Lua-based {@link TriggerEngine} mapping {@link GameEvent}s to DMX</li>
 *   <li>Named presets and cue lists</li>
 *   <li>Protocol servers (Art-Net, sACN, OSC, HTTP, MQTT, WebConsole)</li>
 *   <li>Redstone readback state</li>
 * </ul>
 *
 * <p>Everything is console-agnostic. Console specifics live behind
 * {@link net.minelight.core.api.ConsoleBackend}s.</p>
 */
public final class ConsoleEngine implements ConsoleEngineContext {

    /** DMX frame rate. */
    public static final int DMX_HZ = 44;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Patch patch = new Patch();
    private final TriggerEngine triggers;
    private final List<ProtocolServer> servers = new CopyOnWriteArrayList<>();
    private final List<DmxListener> dmxListeners = new CopyOnWriteArrayList<>();
    private final List<GameEventListener> eventListeners = new CopyOnWriteArrayList<>();

    /** Merged DMX output: universe -> 512 bytes. */
    private final Map<Integer, byte[]> universes = new ConcurrentHashMap<>();

    /** Redstone readback: fixtureId -> on/off. */
    private final Map<Integer, Boolean> redstoneState = new ConcurrentHashMap<>();

    /** Named presets: name -> (fixtureId -> channel-values). */
    private final Map<String, Map<Integer, int[]>> presets = new ConcurrentHashMap<>();

    /** Cue lists: name -> list of cue (preset name or inline). */
    private final Map<String, CueList> cueLists = new ConcurrentHashMap<>();

    private final Path configDir;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running;

    @FunctionalInterface
    public interface GameEventListener {
        void onEvent(GameEvent event);
    }

    /** A named cue list. */
    public static final class CueList {
        public final String name;
        public final List<Cue> cues = new CopyOnWriteArrayList<>();
        private volatile int index;

        public CueList(String name) {
            this.name = name;
        }

        public record Cue(String label, Map<Integer, int[]> levels, double fadeSeconds) {
        }

        public int index() {
            return index;
        }

        public Cue next() {
            if (cues.isEmpty()) {
                return null;
            }
            index = (index + 1) % cues.size();
            return cues.get(index);
        }

        public Cue go(int i) {
            if (i < 0 || i >= cues.size()) {
                return null;
            }
            index = i;
            return cues.get(i);
        }
    }

    public ConsoleEngine(Path configDir) {
        this.configDir = Objects.requireNonNull(configDir);
        this.triggers = new TriggerEngine(this);
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "minelight-engine");
            t.setDaemon(true);
            return t;
        });
    }

    // ---- lifecycle ------------------------------------------------------

    public synchronized void start() throws Exception {
        if (running) {
            return;
        }
        load();
        for (ProtocolServer s : servers) {
            s.start();
        }
        // DMX output pump: merge + notify at DMX_HZ
        scheduler.scheduleAtFixedRate(this::pumpDmx, 0, 1000 / DMX_HZ, TimeUnit.MILLISECONDS);
        // Autosave every 30 s
        scheduler.scheduleAtFixedRate(this::saveQuietly, 30, 30, TimeUnit.SECONDS);
        running = true;
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        for (ProtocolServer s : servers) {
            try {
                s.stop();
            } catch (Exception ignored) {
                // best effort
            }
        }
        saveQuietly();
        scheduler.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    // ---- protocol servers ------------------------------------------------

    public void registerServer(ProtocolServer server) {
        servers.add(server);
        if (running) {
            try {
                server.start();
            } catch (Exception e) {
                throw new RuntimeException("Failed to start " + server.name(), e);
            }
        }
    }

    public List<ProtocolServer> servers() {
        return List.copyOf(servers);
    }

    // ---- events -----------------------------------------------------------

    @Override
    public void emit(GameEvent event) {
        for (GameEventListener l : eventListeners) {
            try {
                l.onEvent(event);
            } catch (Exception ignored) {
            }
        }
        triggers.onEvent(event);
    }

    public void addEventListener(GameEventListener l) {
        eventListeners.add(l);
    }

    // ---- DMX ---------------------------------------------------------------

    /** Set a single DMX channel (merged output). */
    public void setDmx(int universe, int channel, int value) {
        if (channel < 1 || channel > 512) {
            return;
        }
        byte[] buf = universes.computeIfAbsent(universe, u -> new byte[512]);
        buf[channel - 1] = (byte) (value & 0xFF);
    }

    /** Set a fixture's channels from a preset-level array (offset by mode). */
    public void setFixtureLevels(int fixtureId, int[] levels) {
        Patch.Fixture f = patch.byId(fixtureId);
        if (f == null) {
            return;
        }
        int n = Math.min(levels.length, f.mode().footprint());
        for (int i = 0; i < n; i++) {
            setDmx(f.universe(), f.address() + i, levels[i]);
        }
    }

    /** Convenience: set a fixture to an intensity 0-255 (first channel). */
    public void setFixtureIntensity(int fixtureId, int intensity) {
        Patch.Fixture f = patch.byId(fixtureId);
        if (f == null) {
            return;
        }
        setDmx(f.universe(), f.address(), intensity);
    }

    @Override
    public Map<Integer, byte[]> dmxSnapshot() {
        Map<Integer, byte[]> snap = new ConcurrentHashMap<>();
        universes.forEach((u, b) -> snap.put(u, b.clone()));
        return snap;
    }

    @Override
    public void addDmxListener(DmxListener listener) {
        dmxListeners.add(listener);
    }

    private void pumpDmx() {
        if (dmxListeners.isEmpty()) {
            return;
        }
        Map<Integer, byte[]> snap = dmxSnapshot();
        for (DmxListener l : dmxListeners) {
            try {
                l.onDmx(snap);
            } catch (Exception ignored) {
            }
        }
    }

    // ---- patch ---------------------------------------------------------------

    @Override
    public Patch patch() {
        return patch;
    }

    // ---- presets -----------------------------------------------------------

    public void savePreset(String name, Map<Integer, int[]> levels) {
        presets.put(name, new ConcurrentHashMap<>(levels));
    }

    public Map<String, Map<Integer, int[]>> presets() {
        return presets;
    }

    public boolean applyPreset(String name) {
        Map<Integer, int[]> p = presets.get(name);
        if (p == null) {
            return false;
        }
        p.forEach(this::setFixtureLevels);
        return true;
    }

    // ---- cue lists ---------------------------------------------------------

    public CueList cueList(String name) {
        return cueLists.computeIfAbsent(name, CueList::new);
    }

    public Map<String, CueList> cueLists() {
        return cueLists;
    }

    // ---- redstone readback -------------------------------------------------

    public void setRedstone(int fixtureId, boolean on) {
        redstoneState.put(fixtureId, on);
        emit(GameEvent.of(on ? GameEvent.REDSTONE_ON : GameEvent.REDSTONE_OFF,
                "fixtureId", fixtureId));
    }

    public boolean redstone(int fixtureId) {
        return redstoneState.getOrDefault(fixtureId, false);
    }

    public Map<Integer, Boolean> redstoneSnapshot() {
        return Map.copyOf(redstoneState);
    }

    // ---- triggers ------------------------------------------------------------

    public TriggerEngine triggers() {
        return triggers;
    }

    // ---- persistence -----------------------------------------------------------

    private Path configFile() {
        return configDir.resolve("minelight.json");
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configDir);
            JsonObject root = new JsonObject();
            root.add("patch", patch.toJson());
            root.addProperty("nextFixtureId", patch.fixtures().stream()
                    .mapToInt(Patch.Fixture::id).max().orElse(0) + 1);

            JsonObject presetsJson = new JsonObject();
            presets.forEach((name, map) -> {
                JsonObject m = new JsonObject();
                map.forEach((fid, levels) -> {
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (int v : levels) {
                        arr.add(v);
                    }
                    m.add(String.valueOf(fid), arr);
                });
                presetsJson.add(name, m);
            });
            root.add("presets", presetsJson);

            JsonObject triggersJson = new JsonObject();
            triggersJson.addProperty("script", triggers.script());
            root.add("triggers", triggersJson);

            try (Writer w = Files.newBufferedWriter(configFile())) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            System.err.println("[MineLight] Failed to save config: " + e);
        }
    }

    private void saveQuietly() {
        try {
            save();
        } catch (Exception ignored) {
        }
    }

    public synchronized void load() {
        Path f = configFile();
        if (!Files.exists(f)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("patch")) {
                Patch loaded = Patch.fromJson(root.getAsJsonObject("patch"));
                // copy fixtures in
                for (Patch.Fixture fx : loaded.fixtures()) {
                    patch.addFixture(fx.name(), fx.mode(), fx.universe(), fx.address(),
                            fx.x(), fx.y(), fx.z(), fx.kind());
                }
            }
            if (root.has("presets")) {
                JsonObject pj = root.getAsJsonObject("presets");
                for (String name : pj.keySet()) {
                    Map<Integer, int[]> m = new ConcurrentHashMap<>();
                    pj.getAsJsonObject(name).entrySet().forEach(e -> {
                        var arr = e.getValue().getAsJsonArray();
                        int[] levels = new int[arr.size()];
                        for (int i = 0; i < arr.size(); i++) {
                            levels[i] = arr.get(i).getAsInt();
                        }
                        m.put(Integer.parseInt(e.getKey()), levels);
                    });
                    presets.put(name, m);
                }
            }
            if (root.has("triggers")) {
                triggers.setScript(root.getAsJsonObject("triggers").get("script").getAsString());
            }
        } catch (Exception e) {
            System.err.println("[MineLight] Failed to load config: " + e);
        }
    }
}
