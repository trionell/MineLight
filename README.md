# MineLight

**A Minecraft mod that lets you control in-game lighting with real lighting consoles — GrandMA2, GrandMA3, Avolites Titan, and anything that speaks Art-Net, sACN, OSC, MIDI, HTTP, or MQTT.**

MineLight turns your Minecraft world into a fully patched lighting rig. Every redstone lamp, beacon, torch, or custom light becomes a DMX fixture. Drive it from a GrandMA, an Avolites Titan, a Midi Fighter Twister, a phone running an OSC app, Home Assistant, or the built-in browser console. Trigger looks from redstone, command blocks, game events, or Lua scripts.

---

## Why MineLight?

- **Minecraft is the controller** — place fixture blocks, feed them redstone, and they drive real-world lights through your console (Minecraft → console → DMX).
- **Fun** — run your base's lighting like a stadium show. Strobe on a raid, warm fades at sunrise, a lighting desk in your survival world.
- **Highly customizable** — fixtures, presets, cue lists, and a full Lua trigger engine.
- **Redstone integration is a must** — redstone changes drive lights, and console levels can drive redstone (readback).
- **Game-event integration** — command blocks (`/ml event …`), player join/death, time of day, weather, block changes.
- **Console-agnostic** — the same engine talks to every console. Add a console-specific backend when you want polish like native fixture-library export.

---

## Quick start

### Run the engine standalone (no Minecraft needed)

```bash
# build everything
gradle build

# run the headless engine + all protocol servers
gradle :minelight-core:run
```

Then open the built-in console:

```
http://localhost:8090/
```

### Install the Fabric mod

1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.21.8+ and Fabric API.
2. Build the mod:

   ```bash
   gradle :minelight-fabric:build
   ```

   The jar lands in `minelight-fabric/build/libs/`.
3. Drop it into your `mods/` folder.
4. In game, run `/ml console` and open the URL, or press **K**.

> **Note for sandboxed builds:** if `maven.fabricmc.net` or Mojang's servers are unreachable, the Fabric module builds as a *stub* jar so the core engine and tests still pass. On a normal network you get the real mod jar.

---

## Console support

| Console | Protocol | Status | Notes |
|---|---|---|---|
| **GrandMA2** | Art-Net / sACN | ✅ | Generic Art-Net/sACN input; MA-Net session backend planned |
| **GrandMA3** | sACN | ✅ | Native sACN; fixture-library export backend planned |
| **Avolites Titan** | Art-Net / sACN | ✅ | Titan One / Titan Mobile via Art-Net |
| **WebConsole** | built-in | ✅ | zero-install browser console at `:8090` |
| **Midi Fighter Twister** | MIDI | ✅ | CC/note bindings to fixtures |
| **OSC apps** (TouchDesigner, QLC+, phones) | OSC | ✅ | `/minelight/**` |
| **Home Assistant / Node-RED** | MQTT | ✅ | `minelight/#` topics |
| **Anything else** | HTTP REST | ✅ | JSON API at `:8080` |

### Adding a new console

Implement `ConsoleBackend` to add console-specific polish (fixture-library export, session protocols, naming). The engine, protocols, and patch are shared.

```java
public class MyConsoleBackend implements ConsoleBackend {
    public String id() { return "myconsole"; }
    public String displayName() { return "My Console"; }
    public String exportFixtureLibrary(Patch patch) { /* ... */ }
}
```

---

## Protocols

| Protocol | Port | Direction | Use |
|---|---|---|---|
| Art-Net v4 | 6454/udp | in+out | consoles, nodes, MadMapper, ELM |
| sACN (E1.31) | 5568/udp | out | GrandMA3, modern consoles |
| OSC | 8000/udp | in+out | TouchDesigner, QLC+, phone apps |
| HTTP REST | 8080/tcp | in+out | command blocks, scripts, CI |
| MQTT | broker | in+out | Home Assistant, Node-RED |
| WebSocket | 8090/tcp | in+out | WebConsole live sync |
| MIDI | USB/virtual | in | Midi Fighter Twister, controllers |
| mDNS | 5353/udp | discovery | auto-discovery on the LAN |

---

## Fixture blocks (the main way to play)

MineLight adds four blocks. Feed them redstone, right-click to configure, and they drive real lights through your console.

| Block | Input | What it does |
|---|---|---|
| **Dimmer Fixture** | one redstone input | Signal strength 0–15 scales to DMX 0–255 on its channel. Lever = on/off, comparator = analog fader. |
| **RGB Fixture** | three inputs on three faces | North = red, east = green, west = blue (configurable). Three comparator lines = a tiny analog RGB mixer. |
| **Event Fixture** | boolean redstone edge | Each rising edge fires a custom event (e.g. `custom.block4`) at the console. Pressure plate → cue. |
| **Feedback Fixture** | none (output) | Receives the console's level and emits redstone 0–15 proportional to it. Fader at 50% → piston halfway. |

Right-click any fixture block to open its config screen: pick the redstone face, the DMX universe.channel, or the event name. Everything saves with the world.

The coordinate-based patch from earlier versions still works if you want invisible integration — fixture blocks are just the friendly, physical front-end.

### Sound-to-light blocks

Four more blocks turn Minecraft's sounds into light:

| Block | Listens to | What it does |
|---|---|---|
| **Note Fixture** | note blocks in radius | pitch → hue (RGB), instrument → accent. Play a melody, paint the room. |
| **Sound Meter Fixture** | ambient world sound | loudness → dimmer, with a fast attack and smooth decay like a real VU meter. |
| **Beat Fixture** | sudden onsets | each beat/kick/explosion fires a full-scale bump or event — classic strobe trigger. |
| **Spectrum Fixture** | bass / mid / treble | 3-band energy → R/G/B. Bass drop goes red, hi-hat shimmer goes blue. |

Right-click any of them to set detection radius, gain/sensitivity, channel map, and the beat threshold. Feed a Sound Meter from a mob farm, hook a Beat block to a note-block loop, or point a Spectrum block at a jukebox for a living-room visualizer.

---

## In-game commands

```
/ml console                      open the WebConsole URL
/ml patch list
/ml patch add <name> [mode] [universe] [address]
/ml patch remove <id>
/ml fixture <id> intensity <0-255>
/ml preset <name>
/ml cue <list> go [index]
/ml event <kind> [json]          # command-block trigger
/ml script                       # edit the Lua trigger script
```

### Command-block triggers

Put this in a command block:

```
/ml event explosion {"power":4}
```

Then in your Lua trigger script:

```lua
minelight.on("custom.explosion", function(e)
  minelight.preset("strobe")
end)
```

---

## Lua trigger API

```lua
minelight.on("redstone.on", function(e)
  minelight.intensity(e.fixtureId, 255)
end)

minelight.on("redstone.off", function(e)
  minelight.intensity(e.fixtureId, 0)
end)

minelight.on("time.night", function(e)
  minelight.preset("night")
end)

minelight.on("midi.cc", function(e)
  minelight.intensity(1, e.value * 2)
end)
```

| Function | Description |
|---|---|
| `minelight.on(kind, fn)` | register an event handler |
| `minelight.set(id, v1, v2, ...)` | set fixture channels |
| `minelight.intensity(id, v)` | set first channel |
| `minelight.preset(name)` | apply a preset |
| `minelight.cue(list [,index])` | advance / jump a cue list |
| `minelight.log(msg)` | log |
| `minelight.patch()` | table of fixtures |

### Built-in event kinds

`block.lit`, `block.unlit`, `redstone.on`, `redstone.off`, `player.join`, `player.leave`, `player.death`, `time.day`, `time.night`, `weather.clear`, `weather.rain`, `weather.thunder`, `midi.noteon`, `midi.noteoff`, `midi.cc`, `artnet.dmx`, and any `custom.*` from `/ml event` or the HTTP API.

---

## HTTP API examples

```bash
# patch a fixture
curl -X POST localhost:8080/api/patch \
  -d '{"name":"Stage Left","mode":"Dimmer","universe":1,"address":1,"x":10,"y":64,"z":10}'

# set intensity
curl -X POST localhost:8080/api/fixture/1/intensity -d '{"value":200}'

# fire a custom event (command-block friendly)
curl -X POST localhost:8080/api/event \
  -d '{"kind":"custom.explosion","data":{"power":4}}'
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Consoles                           │
│  GrandMA2  GrandMA3  Avolites  WebConsole  MIDI  OSC    │
└────┬─────────┬────────┬────────┬─────────┬──────┬──────┘
     │ Art-Net │ sACN   │ HTTP/WS│  MIDI   │ OSC  │ MQTT │
┌────▼─────────▼────────▼────────▼─────────▼──────▼──────┐
│              MineLight core (console-agnostic)           │
│  ConsoleEngine · Patch · TriggerEngine (Lua) · Presets  │
│  CueLists · Redstone readback · ProtocolServers         │
└──────────────────────────┬──────────────────────────────┘
                           │ Fabric bridge
┌──────────────────────────▼──────────────────────────────┐
│                     Minecraft 1.21.x                      │
│  /ml commands · redstone polling · game events · keybinds│
└──────────────────────────────────────────────────────────┘
```

- **`minelight-core`** — pure Java 21, no Minecraft dependencies. Unit-tested.
- **`minelight-fabric`** — thin Fabric glue: commands, keybinds, world polling.

---

## Building

```bash
gradle build                 # build + test everything
gradle :minelight-core:test  # run core tests
gradle :minelight-fabric:build
```

Requirements: JDK 21+. Gradle 9.x is used from your system `gradle`.

---

## Roadmap

- [ ] GrandMA2 fixture-library (`.xml`) export backend
- [ ] GrandMA3 MVR / GDTF export backend
- [ ] Avolites Titan personality file export
- [x] In-world fixture blocks (Dimmer / RGB / Event / Feedback) + right-click config GUI
- [ ] Moving-head fixtures with pan/tilt
- [ ] Pixel mapping for beacon arrays
- [x] Sound-to-light: Note / Sound Meter / Beat / Spectrum blocks
- [ ] Multi-world profiles

---

## License

GPL-3.0-or-later. See `LICENSE`.
