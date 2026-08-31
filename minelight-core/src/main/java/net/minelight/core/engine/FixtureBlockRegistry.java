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
    private int nextId = 1;

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

                switch (port.action()) {
                    case SET_CHANNEL -> {
                        if (prev == null || prev != raw) {
                            lastInput.put(key, raw);
                            if (b.type() == FixtureBlock.Type.FEEDBACK) {
                                // feedback blocks are outputs; skip TX
                                break;
                            }
                            int value = raw * 255 / 15;
                            engine.setDmx(port.universe(), port.channel(), value);
                            emitBlockEvent("block.dmx", b, port, raw, value);
                        }
                    }
                    case EMIT_EVENT -> {
                        boolean on = raw > 0;
                        boolean was = prev != null && prev > 0;
                        if (on && !was) {
                            emitBlockEvent(port.event(), b, port, raw, raw * 255 / 15);
                        }
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
