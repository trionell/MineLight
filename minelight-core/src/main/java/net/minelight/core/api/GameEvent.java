package net.minelight.core.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A typed game event flowing from Minecraft into the lighting engine.
 *
 * <p>Events are produced by the Minecraft-side bridge (block changes,
 * player actions, command-block triggers via {@code /ml event <name>},
 * redstone changes) and consumed by the trigger engine, which maps them
 * to DMX output via Lua scripts, presets, and cue lists.</p>
 */
public final class GameEvent {

    /** Well-known event kinds. Custom kinds are allowed (command blocks). */
    public static final String BLOCK_LIT = "block.lit";
    public static final String BLOCK_UNLIT = "block.unlit";
    public static final String REDSTONE_ON = "redstone.on";
    public static final String REDSTONE_OFF = "redstone.off";
    public static final String PLAYER_JOIN = "player.join";
    public static final String PLAYER_LEAVE = "player.leave";
    public static final String PLAYER_DEATH = "player.death";
    public static final String TIME_DAY = "time.day";
    public static final String TIME_NIGHT = "time.night";
    public static final String WEATHER_CLEAR = "weather.clear";
    public static final String WEATHER_RAIN = "weather.rain";
    public static final String WEATHER_THUNDER = "weather.thunder";
    /** Fired by command blocks / chat via {@code /ml event <name>}. */
    public static final String CUSTOM = "custom";

    private final String kind;
    private final Map<String, Object> data;
    private final long timestamp;

    public GameEvent(String kind, Map<String, Object> data) {
        this.kind = kind;
        this.data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.timestamp = System.currentTimeMillis();
    }

    public static GameEvent of(String kind) {
        return new GameEvent(kind, Map.of());
    }

    public static GameEvent of(String kind, String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return new GameEvent(kind, m);
    }

    public String kind() {
        return kind;
    }

    public Map<String, Object> data() {
        return data;
    }

    public long timestamp() {
        return timestamp;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key, String def) {
        Object v = data.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public int getInt(String key, int def) {
        Object v = data.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public String toString() {
        return "GameEvent[" + kind + " " + data + "]";
    }
}
