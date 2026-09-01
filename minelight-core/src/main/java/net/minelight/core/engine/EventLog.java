package net.minelight.core.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minelight.core.api.GameEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A bounded ring of the most recent {@link GameEvent}s.
 *
 * <p>Everything that flows into the engine — redstone edges, block DMX
 * writes, sound onsets, inbound Art-Net/OSC/MIDI from a real desk — passes
 * through {@link ConsoleEngine#emit}, so a tap there is the one place that
 * sees every signal. Monitors (the WebConsole, the HTTP API) read the ring
 * instead of each growing their own listener and buffer.</p>
 *
 * <p>Every entry carries a monotonically increasing sequence number so a
 * client that reconnects can ask for "everything after N" rather than
 * replaying the whole ring.</p>
 */
public final class EventLog {

    /** How many events to keep. Roughly ten seconds of a busy show. */
    public static final int CAPACITY = 400;

    /** One logged event: the game event plus its sequence number. */
    public record Entry(long seq, GameEvent event) {

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("seq", seq);
            o.addProperty("kind", event.kind());
            o.addProperty("at", event.timestamp());
            JsonObject data = new JsonObject();
            for (Map.Entry<String, Object> e : event.data().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Number n) {
                    data.addProperty(e.getKey(), n);
                } else if (v instanceof Boolean b) {
                    data.addProperty(e.getKey(), b);
                } else {
                    data.addProperty(e.getKey(), String.valueOf(v));
                }
            }
            o.add("data", data);
            return o;
        }
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private long nextSeq = 1;

    public EventLog() {
        this(CAPACITY);
    }

    public EventLog(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void add(GameEvent event) {
        entries.addLast(new Entry(nextSeq++, event));
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    /** The highest sequence number issued so far (0 when nothing logged). */
    public synchronized long lastSeq() {
        return nextSeq - 1;
    }

    /** Every retained entry, oldest first. */
    public synchronized List<Entry> all() {
        return new ArrayList<>(entries);
    }

    /** Entries newer than {@code afterSeq}, oldest first. */
    public synchronized List<Entry> since(long afterSeq) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.seq() > afterSeq) {
                out.add(e);
            }
        }
        return out;
    }

    public synchronized JsonArray toJson(long afterSeq) {
        JsonArray arr = new JsonArray();
        for (Entry e : since(afterSeq)) {
            arr.add(e.toJson());
        }
        return arr;
    }
}
