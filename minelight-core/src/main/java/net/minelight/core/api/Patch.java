package net.minelight.core.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * The lighting patch for a Minecraft world.
 *
 * <p>The patch is a list of {@link Fixture}s — virtual lights placed in the
 * world — each with an id, display name, DMX footprint ({@link FixtureMode}),
 * and channel layout. The patch is what gets exported to a console as an
 * Art-Net/sACN personality description or a console-specific fixture library
 * file.</p>
 *
 * <p>This is intentionally <em>not</em> tied to any console. A GrandMA2,
 * a GrandMA3, an Avolites Titan, or the built-in WebPanel all consume the
 * same patch; the per-console adaptation happens in the {@code ProtocolServer}
 * and {@code ConsoleBackend} layers.</p>
 */
public final class Patch {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One channel in a fixture mode: name + default value (0-255). */
    public record Channel(String name, int defaultValue) {
        public Channel {
            if (defaultValue < 0 || defaultValue > 255) {
                throw new IllegalArgumentException("defaultValue out of range: " + defaultValue);
            }
        }

        public static Channel of(String name) {
            return new Channel(name, 0);
        }

        public static Channel of(String name, int defaultValue) {
            return new Channel(name, defaultValue);
        }
    }

    /**
     * A fixture mode: the DMX channel layout. Most Minecraft lights map to a
     * single "intensity" channel, but RGB beacons, strobes, and movers use
     * multi-channel modes.
     */
    public record FixtureMode(String name, Channel[] channels) {
        public int footprint() {
            return channels.length;
        }
    }

    /** Common fixture modes. */
    public static final FixtureMode DIMMER = new FixtureMode("Dimmer",
            new Channel[]{Channel.of("Intensity")});

    public static final FixtureMode RGB = new FixtureMode("RGB",
            new Channel[]{Channel.of("Red"), Channel.of("Green"), Channel.of("Blue")});

    public static final FixtureMode RGB_DIMMER = new FixtureMode("RGB+Dimmer",
            new Channel[]{Channel.of("Intensity"), Channel.of("Red"),
                    Channel.of("Green"), Channel.of("Blue")});

    public static final FixtureMode STROBE = new FixtureMode("Strobe",
            new Channel[]{Channel.of("Intensity"), Channel.of("Rate")});

    /**
     * A single patched light in the world.
     *
     * @param id          stable id (never reused)
     * @param name        display name shown on consoles
     * @param mode        DMX channel layout
     * @param universe    DMX universe (1-based)
     * @param address     DMX address within the universe (1-based)
     * @param x           block x
     * @param y           block y
     * @param z           block z
     * @param kind        semantic kind: "lamp", "beacon", "torch", "mover", ...
     * @param redstone    whether this fixture currently reports redstone state back
     */
    public record Fixture(int id, String name, FixtureMode mode,
                          int universe, int address,
                          int x, int y, int z,
                          String kind, boolean redstone) {
        public Fixture withPosition(int nx, int ny, int nz) {
            return new Fixture(id, name, mode, universe, address, nx, ny, nz, kind, redstone);
        }

        public Fixture withRedstone(boolean r) {
            return new Fixture(id, name, mode, universe, address, x, y, z, kind, r);
        }
    }

    private final java.util.List<Fixture> fixtures = new java.util.ArrayList<>();
    private int nextId = 1;

    /** Add a fixture and return its assigned id. */
    public synchronized int addFixture(String name, FixtureMode mode,
                                       int universe, int address,
                                       int x, int y, int z, String kind) {
        int id = nextId++;
        fixtures.add(new Fixture(id, name, mode, universe, address, x, y, z, kind, true));
        return id;
    }

    public synchronized java.util.List<Fixture> fixtures() {
        return java.util.List.copyOf(fixtures);
    }

    public synchronized Fixture byId(int id) {
        for (Fixture f : fixtures) {
            if (f.id() == id) {
                return f;
            }
        }
        return null;
    }

    public synchronized boolean remove(int id) {
        return fixtures.removeIf(f -> f.id() == id);
    }

    public synchronized int size() {
        return fixtures.size();
    }

    // ---- serialization -------------------------------------------------

    public synchronized JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("nextId", nextId);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (Fixture f : fixtures) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("name", f.name());
            o.addProperty("mode", f.mode().name());
            o.addProperty("universe", f.universe());
            o.addProperty("address", f.address());
            o.addProperty("x", f.x());
            o.addProperty("y", f.y());
            o.addProperty("z", f.z());
            o.addProperty("kind", f.kind());
            o.addProperty("redstone", f.redstone());
            arr.add(o);
        }
        root.add("fixtures", arr);
        return root;
    }

    public static Patch fromJson(JsonObject root) {
        Patch p = new Patch();
        if (root == null) {
            return p;
        }
        p.nextId = root.has("nextId") ? root.get("nextId").getAsInt() : 1;
        if (root.has("fixtures")) {
            for (var el : root.getAsJsonArray("fixtures")) {
                JsonObject o = el.getAsJsonObject();
                String modeName = o.get("mode").getAsString();
                FixtureMode mode = switch (modeName) {
                    case "RGB" -> RGB;
                    case "RGB+Dimmer" -> RGB_DIMMER;
                    case "Strobe" -> STROBE;
                    default -> DIMMER;
                };
                p.fixtures.add(new Fixture(
                        o.get("id").getAsInt(),
                        o.get("name").getAsString(),
                        mode,
                        o.get("universe").getAsInt(),
                        o.get("address").getAsInt(),
                        o.get("x").getAsInt(),
                        o.get("y").getAsInt(),
                        o.get("z").getAsInt(),
                        o.get("kind").getAsString(),
                        o.has("redstone") && o.get("redstone").getAsBoolean()));
            }
        }
        return p;
    }

    public String toJsonString() {
        return GSON.toJson(toJson());
    }
}
