package net.minelight.core.scripting;

import net.minelight.core.api.GameEvent;
import net.minelight.core.engine.ConsoleEngine;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua-based trigger engine mapping {@link GameEvent}s to DMX output.
 *
 * <p>Users write Lua in a profile. The script registers handlers like:</p>
 *
 * <pre>{@code
 * minelight.on("redstone.on", function(e)
 *   minelight.set(e.fixtureId, 255)
 * end)
 *
 * minelight.on("custom.explosion", function(e)
 *   minelight.preset("strobe")
 * end)
 * }</pre>
 *
 * <p>The {@code minelight} Lua API:</p>
 * <ul>
 *   <li>{@code minelight.on(kind, fn)} — register an event handler</li>
 *   <li>{@code minelight.set(fixtureId, value[, ...])} — set fixture channels</li>
 *   <li>{@code minelight.intensity(fixtureId, value)} — set first channel</li>
 *   <li>{@code minelight.dmx(universe, channel, value)} — set a raw channel</li>
 *   <li>{@code minelight.pulse(universe, channel[, value[, ms]])} — momentary
 *       trigger on a raw channel; this is how an event reaches a desk that
 *       only speaks DMX</li>
 *   <li>{@code minelight.preset(name)} — apply a named preset</li>
 *   <li>{@code minelight.cue(listName)} — advance a cue list</li>
 *   <li>{@code minelight.cue(listName, index)} — jump to cue index</li>
 *   <li>{@code minelight.log(msg)} — log to the console</li>
 *   <li>{@code minelight.patch()} — table of fixtures</li>
 * </ul>
 */
public final class TriggerEngine {

    private final ConsoleEngine engine;
    private final Map<String, java.util.List<LuaValue>> handlers = new ConcurrentHashMap<>();
    private volatile String script = DEFAULT_SCRIPT;
    private Globals globals;

    public static final String DEFAULT_SCRIPT = """
            -- MineLight trigger script
            -- Available: minelight.on, minelight.set, minelight.intensity,
            --            minelight.dmx, minelight.pulse,
            --            minelight.preset, minelight.cue, minelight.log, minelight.patch

            -- Any game event can be handed to a lighting desk as a momentary
            -- DMX trigger. Point a console's DMX remote at the channel.
            -- minelight.on("player.death", function(e)
            --   minelight.pulse(1, 100)
            -- end)

            minelight.on("redstone.on", function(e)
              minelight.intensity(e.fixtureId, 255)
            end)

            minelight.on("redstone.off", function(e)
              minelight.intensity(e.fixtureId, 0)
            end)

            minelight.on("time.night", function(e)
              minelight.preset("night")
            end)

            minelight.on("time.day", function(e)
              minelight.preset("day")
            end)
            """;

    public TriggerEngine(ConsoleEngine engine) {
        this.engine = engine;
        reload();
    }

    public synchronized String script() {
        return script;
    }

    public synchronized void setScript(String s) {
        this.script = s == null || s.isBlank() ? DEFAULT_SCRIPT : s;
        reload();
    }

    public synchronized void reload() {
        handlers.clear();
        globals = JsePlatform.standardGlobals();
        globals.set("minelight", buildApi());
        try {
            globals.load(script, "minelight-script").call();
        } catch (Exception e) {
            System.err.println("[MineLight] Lua script error: " + e.getMessage());
        }
    }

    public void onEvent(GameEvent event) {
        var list = handlers.get(event.kind());
        if (list == null || list.isEmpty()) {
            return;
        }
        LuaValue ev = eventToLua(event);
        for (LuaValue fn : list) {
            try {
                fn.call(ev);
            } catch (Exception e) {
                System.err.println("[MineLight] Lua handler error (" + event.kind() + "): " + e.getMessage());
            }
        }
    }

    // ---- Lua API ----------------------------------------------------------

    private LuaValue buildApi() {
        LuaTable api = new LuaTable();

        api.set("on", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue kind, LuaValue fn) {
                handlers.computeIfAbsent(kind.checkjstring(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                        .add(fn);
                return LuaValue.NIL;
            }
        });

        api.set("set", new VarArgFunction() {
            @Override
            public LuaValue invoke(org.luaj.vm2.Varargs args) {
                int fixtureId = args.arg(1).checkint();
                int n = args.narg() - 1;
                int[] levels = new int[n];
                for (int i = 0; i < n; i++) {
                    levels[i] = args.arg(i + 2).checkint();
                }
                engine.setFixtureLevels(fixtureId, levels);
                return LuaValue.NIL;
            }
        });

        api.set("intensity", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue fixtureId, LuaValue value) {
                engine.setFixtureIntensity(fixtureId.checkint(), value.checkint());
                return LuaValue.NIL;
            }
        });

        // Raw channel access. The fixture-scoped calls above need something
        // patched; a script reacting to a game event usually just wants to
        // poke the channel a console is listening on.
        api.set("dmx", new VarArgFunction() {
            @Override
            public LuaValue invoke(org.luaj.vm2.Varargs args) {
                engine.setDmx(args.arg(1).checkint(), args.arg(2).checkint(),
                        args.arg(3).checkint());
                return LuaValue.NIL;
            }
        });

        api.set("pulse", new VarArgFunction() {
            @Override
            public LuaValue invoke(org.luaj.vm2.Varargs args) {
                int universe = args.arg(1).checkint();
                int channel = args.arg(2).checkint();
                int value = args.narg() > 2 ? args.arg(3).checkint() : 255;
                long ms = args.narg() > 3 ? args.arg(4).checklong() : ConsoleEngine.PULSE_MS;
                engine.pulseDmx(universe, channel, value, ms);
                return LuaValue.NIL;
            }
        });

        api.set("preset", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue name) {
                engine.applyPreset(name.checkjstring());
                return LuaValue.NIL;
            }
        });

        api.set("cue", new VarArgFunction() {
            @Override
            public LuaValue invoke(org.luaj.vm2.Varargs args) {
                String list = args.arg(1).checkjstring();
                ConsoleEngine.CueList cl = engine.cueList(list);
                ConsoleEngine.CueList.Cue cue;
                if (args.narg() > 1) {
                    cue = cl.go(args.arg(2).checkint());
                } else {
                    cue = cl.next();
                }
                if (cue != null && cue.levels() != null) {
                    cue.levels().forEach(engine::setFixtureLevels);
                }
                return LuaValue.NIL;
            }
        });

        api.set("log", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue msg) {
                System.out.println("[MineLight] " + msg.checkjstring());
                return LuaValue.NIL;
            }
        });

        api.set("patch", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable t = new LuaTable();
                int i = 1;
                for (PatchFixtureAdapter pf : PatchFixtureAdapter.from(engine.patch())) {
                    t.set(i++, pf.toLua());
                }
                return t;
            }
        });

        return api;
    }

    private static LuaValue eventToLua(GameEvent e) {
        LuaTable t = new LuaTable();
        t.set("kind", e.kind());
        t.set("timestamp", e.timestamp());
        e.data().forEach((k, v) -> {
            if (v instanceof Number n) {
                t.set(k, n.doubleValue());
            } else if (v instanceof Boolean b) {
                t.set(k, LuaValue.valueOf(b));
            } else {
                t.set(k, String.valueOf(v));
            }
        });
        return t;
    }

    /** Small adapter so TriggerEngine doesn't need Gson on the hot path. */
    private record PatchFixtureAdapter(int id, String name, int universe, int address,
                                       int x, int y, int z, String kind) {
        static java.util.List<PatchFixtureAdapter> from(net.minelight.core.api.Patch patch) {
            java.util.List<PatchFixtureAdapter> out = new java.util.ArrayList<>();
            for (var f : patch.fixtures()) {
                out.add(new PatchFixtureAdapter(f.id(), f.name(), f.universe(), f.address(),
                        f.x(), f.y(), f.z(), f.kind()));
            }
            return out;
        }

        LuaValue toLua() {
            LuaTable t = new LuaTable();
            t.set("id", id);
            t.set("name", name);
            t.set("universe", universe);
            t.set("address", address);
            t.set("x", x);
            t.set("y", y);
            t.set("z", z);
            t.set("kind", kind);
            return t;
        }
    }
}
