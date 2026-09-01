package net.minelight.core.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minelight.core.api.FixtureBlock;
import net.minelight.core.api.GameEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Tracks placed fixture blocks and drives the Minecraft-as-controller flow.
 *
 * <p>Each game tick the bridge calls {@link #tick}, supplying a function that
 * reports the redstone power (0–15) on a given face of a given block
 * position. The registry diffs power per port and:</p>
 *
 * <ul>
 *   <li><b>SET_CHANNEL</b> ports — scales the input to 0–255 and writes it to
 *       the merged DMX output (so a connected console passes it through to
 *       real lights). Fires {@code block.dmx}.</li>
 *   <li><b>EMIT_EVENT</b> ports — on a rising edge (0 → &gt;0) emits the
 *       configured custom event into the trigger engine.</li>
 *   <li><b>FEEDBACK</b> blocks — read the current DMX level (driven by a
 *       console) and expose it via {@link #feedbackLevel} so the block can
 *       emit proportional redstone.</li>
 * </ul>
 */
public final class FixtureBlockRegistry {

    private final ConsoleEngine engine;
    private final Map<Integer, FixtureBlock> blocks = new ConcurrentHashMap<>();
    /** Last raw input value (0–15) per (blockId, portName). */
    private final Map<String, Integer> lastInput = new ConcurrentHashMap<>();
    /** Last feedback level (0–15) per blockId. */
    private final Map<Integer, Integer> feedback = new ConcurrentHashMap<>();
    /** Live per-port telemetry for monitors, keyed the same as {@link #lastInput}. */
    private final Map<String, PortState> portStates = new ConcurrentHashMap<>();
    private int nextId = 1;

    /**
     * What a single port is doing right now.
     *
     * <p>The registry already diffs every port each tick to decide whether to
     * transmit; recording the result costs nothing and is the only place the
     * raw redstone strength behind a DMX value is visible. Without it a
     * monitor can show the level on the wire but not the input that caused
     * it.</p>
     */
    public static final class PortState {
        /** Redstone strength last read on the port's face, 0–15. */
        volatile int raw;
        /** Value last written to DMX by this port, 0–255. */
        volatile int value;
        /** Wall-clock time of the last change. */
        volatile long changedAt;
        /** How many times an EMIT_EVENT port has fired. */
        volatile long fires;

        public int raw() {
            return raw;
        }

        public int value() {
            return value;
        }

        public long changedAt() {
            return changedAt;
        }

        public long fires() {
            return fires;
        }
    }

    /**
     * @param engine the engine to route DMX and events through
     */
    public FixtureBlockRegistry(ConsoleEngine engine) {
        this.engine = engine;
    }

    public synchronized FixtureBlock add(FixtureBlock.Type type, String name, int x, int y, int z) {
        int id = nextId++;
        FixtureBlock b = new FixtureBlock(id, name, type, x, y, z, FixtureBlock.defaultPorts(type, id));
        blocks.put(id, b);
        return b;
    }

    public synchronized void add(FixtureBlock block) {
        blocks.put(block.id(), block);
        nextId = Math.max(nextId, block.id() + 1);
    }

    public boolean remove(int id) {
        feedback.remove(id);
        portStates.keySet().removeIf(k -> k.startsWith(id + ":"));
        lastInput.keySet().removeIf(k -> k.startsWith(id + ":"));
        return blocks.remove(id) != null;
    }

    public FixtureBlock byId(int id) {
        return blocks.get(id);
    }

    /** Find the block at a position, or null. */
    public FixtureBlock at(int x, int y, int z) {
        for (FixtureBlock b : blocks.values()) {
            if (b.x() == x && b.y() == y && b.z() == z) {
                return b;
            }
        }
        return null;
    }

    public List<FixtureBlock> all() {
        return new ArrayList<>(blocks.values());
    }

    public int size() {
        return blocks.size();
    }

    /** Current feedback level (0–15) for a feedback block. */
    public int feedbackLevel(int blockId) {
        return feedback.getOrDefault(blockId, 0);
    }

    /** Live telemetry for one port, or null if it has never been ticked. */
    public PortState portState(int blockId, String portName) {
        return portStates.get(blockId + ":" + portName);
    }

    /**
     * A monitoring snapshot: every block, its config, and its live values.
     *
     * <p>Port values are read back out of the merged DMX buffer rather than
     * from the last write, so a channel a real desk is driving reads the
     * level that would actually leave on the wire — which is the question a
     * monitor is really asking.</p>
     */
    public JsonArray liveJson() {
        Map<Integer, byte[]> dmx = engine.dmxSnapshot();
        JsonArray arr = new JsonArray();
        for (FixtureBlock b : blocks.values()) {
            JsonObject o = b.toJson();
            JsonArray ports = new JsonArray();
            for (FixtureBlock.PortMapping port : b.ports()) {
                JsonObject po = port.toJson();
                PortState state = portStates.get(b.id() + ":" + port.name());
                po.addProperty("raw", state == null ? 0 : state.raw());
                po.addProperty("changedAt", state == null ? 0 : state.changedAt());
                if (port.action() == FixtureBlock.Action.EMIT_EVENT) {
                    po.addProperty("fires", state == null ? 0 : state.fires());
                } else {
                    po.addProperty("dmx", channelValue(dmx, port.universe(), port.channel()));
                }
                ports.add(po);
            }
            o.add("ports", ports);
            if (b.type() == FixtureBlock.Type.FEEDBACK) {
                o.addProperty("feedbackLevel", feedbackLevel(b.id()));
            }
            if (b.type() == FixtureBlock.Type.RGB) {
                o.addProperty("color", rgbHex(b, dmx));
            }
            arr.add(o);
        }
        return arr;
    }

    private static int channelValue(Map<Integer, byte[]> dmx, int universe, int channel) {
        byte[] buf = dmx.get(universe);
        if (buf == null || channel < 1 || channel > buf.length) {
            return 0;
        }
        return buf[channel - 1] & 0xFF;
    }

    /** The colour an RGB block's three channels currently add up to, as #rrggbb. */
    private static String rgbHex(FixtureBlock b, Map<Integer, byte[]> dmx) {
        int[] rgb = new int[3];
        for (FixtureBlock.PortMapping port : b.ports()) {
            int i = switch (port.name()) {
                case "r" -> 0;
                case "g" -> 1;
                case "b" -> 2;
                default -> -1;
            };
            if (i >= 0) {
                rgb[i] = channelValue(dmx, port.universe(), port.channel());
            }
        }
        return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }

    /**
     * Advance one game tick.
     *
     * @param powerAt (x,y,z,side) -> redstone power 0–15 on that face.
     *                For {@link FixtureBlock.Side#ANY} the bridge should pass
     *                the strongest face value.
     */
    public void tick(PowerReader power) {
        for (FixtureBlock b : blocks.values()) {
            for (FixtureBlock.PortMapping port : b.ports()) {
                int raw = power.read(b.x(), b.y(), b.z(), port.side());
                String key = b.id() + ":" + port.name();
                Integer prev = lastInput.get(key);
                PortState state = portStates.computeIfAbsent(key, k -> new PortState());

                switch (port.action()) {
                    case SET_CHANNEL -> {
                        if (prev == null || prev != raw) {
                            lastInput.put(key, raw);
                            if (b.type() == FixtureBlock.Type.FEEDBACK) {
                                // feedback blocks are outputs; skip TX
                                break;
                            }
                            int value = raw * 255 / 15;
                            state.raw = raw;
                            state.value = value;
                            state.changedAt = System.currentTimeMillis();
                            engine.setDmx(port.universe(), port.channel(), value);
                            emitBlockEvent("block.dmx", b, port, raw, value);
                        }
                    }
                    case EMIT_EVENT -> {
                        boolean on = raw > 0;
                        boolean was = prev != null && prev > 0;
                        if (on && !was) {
                            state.fires++;
                            state.changedAt = System.currentTimeMillis();
                            emitBlockEvent(port.event(), b, port, raw, raw * 255 / 15);
                        }
                        state.raw = raw;
                        lastInput.put(key, raw);
                    }
                }
            }

            // feedback: pull the console-driven level for this block
            if (b.type() == FixtureBlock.Type.FEEDBACK) {
                FixtureBlock.PortMapping port = b.ports().isEmpty() ? null : b.ports().get(0);
                if (port != null) {
                    byte[] uni = engine.dmxSnapshot().get(port.universe());
                    int level = uni != null && port.channel() >= 1 && port.channel() <= 512
                            ? uni[port.channel() - 1] & 0xFF : 0;
                    feedback.put(b.id(), level * 15 / 255);
                }
            }
        }
    }

    private void emitBlockEvent(String kind, FixtureBlock b, FixtureBlock.PortMapping port,
                                int raw, int value) {
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("blockId", b.id());
        data.put("blockName", b.name());
        data.put("blockType", b.type().name());
        data.put("port", port.name());
        data.put("x", b.x());
        data.put("y", b.y());
        data.put("z", b.z());
        data.put("raw", raw);
        data.put("value", value);
        if (port.action() == FixtureBlock.Action.SET_CHANNEL) {
            data.put("universe", port.universe());
            data.put("channel", port.channel());
        }
        engine.emit(new GameEvent(kind, data));
    }

    @FunctionalInterface
    public interface PowerReader {
        int read(int x, int y, int z, FixtureBlock.Side side);
    }

    // ---- persistence -------------------------------------------------------

    public synchronized JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("nextId", nextId);
        JsonArray arr = new JsonArray();
        for (FixtureBlock b : blocks.values()) {
            arr.add(b.toJson());
        }
        o.add("blocks", arr);
        return o;
    }

    public synchronized void load(JsonObject o) {
        if (o == null) {
            return;
        }
        nextId = o.has("nextId") ? o.get("nextId").getAsInt() : 1;
        if (o.has("blocks")) {
            for (var el : o.getAsJsonArray("blocks")) {
                FixtureBlock b = FixtureBlock.fromJson(el.getAsJsonObject());
                blocks.put(b.id(), b);
            }
        }
    }

    /** A functional interface for reading per-face redstone power. */
    public interface PowerReaderFn extends BiFunction<int[], FixtureBlock.Side, Integer> {
    }
}
