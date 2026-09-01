package net.minelight.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A block-anchored lighting fixture — the main way players interact with
 * MineLight in-game.
 *
 * <p>Where a plain {@link Patch.Fixture} is an invisible coordinate reference,
 * a {@code FixtureBlock} is an actual placed block the player feeds redstone
 * into. Each block has one or more logical <em>ports</em>; a port maps a
 * redstone input (a block face, or "any") to an output action — set a DMX
 * channel, or emit a custom console event.</p>
 *
 * <p>Block types:</p>
 * <ul>
 *   <li>{@link Type#DIMMER} — one input; redstone strength 0–15 scales to a
 *       DMX value 0–255 on a configured channel.</li>
 *   <li>{@link Type#RGB} — three inputs on three faces (e.g. north=R, east=G,
 *       west=B); each strength drives one channel of an RGB group.</li>
 *   <li>{@link Type#EVENT} — boolean; a rising edge fires a custom event
 *       (e.g. {@code custom.explosion}) at the console.</li>
 *   <li>{@link Type#FEEDBACK} — output only; receives a console level and
 *       emits redstone strength proportional to it.</li>
 * </ul>
 */
public final class FixtureBlock {

    public enum Type {
        DIMMER, RGB, EVENT, FEEDBACK
    }

    /** Which face of the block a port reads. ANY means strongest of all faces. */
    public enum Side {
        ANY, NORTH, EAST, SOUTH, WEST, UP, DOWN
    }

    /** What a port does with its input value. */
    public enum Action {
        /** Scale input to a DMX channel value. */
        SET_CHANNEL,
        /** Emit a custom event on rising edge. */
        EMIT_EVENT
    }

    /**
     * One logical port on a fixture block.
     *
     * @param name     port label ("in", "r", "g", "b", "out")
     * @param side     which face to read
     * @param action   what to do with the value
     * @param universe DMX universe for SET_CHANNEL
     * @param channel  DMX channel for SET_CHANNEL
     * @param event    event kind for EMIT_EVENT
     */
    public record PortMapping(String name, Side side, Action action,
                              int universe, int channel, String event) {

        public static PortMapping channel(String name, Side side, int universe, int channel) {
            return new PortMapping(name, side, Action.SET_CHANNEL, universe, channel, null);
        }

        public static PortMapping event(String name, Side side, String event) {
            return new PortMapping(name, side, Action.EMIT_EVENT, 0, 0, event);
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.addProperty("side", side.name());
            o.addProperty("action", action.name());
            o.addProperty("universe", universe);
            o.addProperty("channel", channel);
            if (event != null) {
                o.addProperty("event", event);
            }
            return o;
        }

        static PortMapping fromJson(JsonObject o) {
            return new PortMapping(
                    o.get("name").getAsString(),
                    Side.valueOf(o.get("side").getAsString()),
                    Action.valueOf(o.get("action").getAsString()),
                    o.has("universe") ? o.get("universe").getAsInt() : 0,
                    o.has("channel") ? o.get("channel").getAsInt() : 0,
                    o.has("event") ? o.get("event").getAsString() : null);
        }
    }

    private final int id;
    private final String name;
    private final Type type;
    private final int x, y, z;
    private final List<PortMapping> ports;

    public FixtureBlock(int id, String name, Type type, int x, int y, int z, List<PortMapping> ports) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ports = new ArrayList<>(ports);
    }

    public int id() { return id; }
    public String name() { return name; }
    public Type type() { return type; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public List<PortMapping> ports() { return List.copyOf(ports); }

    /** Replace a port's mapping (used by the in-game config GUI). */
    public void setPort(String portName, PortMapping mapping) {
        for (int i = 0; i < ports.size(); i++) {
            if (ports.get(i).name().equals(portName)) {
                ports.set(i, mapping);
                return;
            }
        }
        ports.add(mapping);
    }

    /** Default ports for a freshly placed block of this type. */
    public static List<PortMapping> defaultPorts(Type type, int id) {
        List<PortMapping> p = new ArrayList<>();
        switch (type) {
            case DIMMER -> p.add(PortMapping.channel("in", Side.ANY, 1, id));
            case RGB -> {
                p.add(PortMapping.channel("r", Side.NORTH, 1, id * 3 - 2));
                p.add(PortMapping.channel("g", Side.EAST, 1, id * 3 - 1));
                p.add(PortMapping.channel("b", Side.WEST, 1, id * 3));
            }
            case EVENT -> p.add(PortMapping.event("in", Side.ANY, "custom.block" + id));
            case FEEDBACK -> p.add(PortMapping.channel("out", Side.ANY, 1, id));
        }
        return p;
    }

    // ---- serialization ---------------------------------------------------

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("type", type.name());
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        JsonArray arr = new JsonArray();
        for (PortMapping p : ports) {
            arr.add(p.toJson());
        }
        o.add("ports", arr);
        return o;
    }

    public static FixtureBlock fromJson(JsonObject o) {
        List<PortMapping> ports = new ArrayList<>();
        if (o.has("ports")) {
            for (var el : o.getAsJsonArray("ports")) {
                ports.add(PortMapping.fromJson(el.getAsJsonObject()));
            }
        }
        return new FixtureBlock(
                o.get("id").getAsInt(),
                o.get("name").getAsString(),
                Type.valueOf(o.get("type").getAsString()),
                o.get("x").getAsInt(),
                o.get("y").getAsInt(),
                o.get("z").getAsInt(),
                ports);
    }
}
