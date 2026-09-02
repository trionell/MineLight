# Roadmap

Planned and finished work. Update it as part of the change, not afterwards: move an item
when the work lands, and add newly discovered defects as you find them.

Git history holds the detail — keep entries here short enough to scan.

---

## Next

### Sound blocks react to real audible sound

The headline gap. Sound blocks are currently a stub: they do not read audio at all. The
only inputs are punching a note block and a TNT entity spawning, so a sound block flickers
when TNT is *lit* and does nothing when it explodes.

**Requirement:** sound blocks react to the sounds a player actually hears, at the moment
they hear them.

The server has no audio, but it originates nearly every gameplay sound with position,
category, volume and pitch. Hooking that path is the route — it needs the mod's first
mixin, and `SoundBridge` reworked from a single global scalar to per-block positional
mixing.

Open question to settle before building: "audible" measured from the player's ears or
from the block's position. It changes the design and should be a deliberate choice.

Not in scope here: note blocks get their own dedicated block later.

### Sound defects worth fixing regardless

Independent of the rework above, and small:

- `radius` is ignored for LEVEL, BEAT and SPECTRUM — the level is one global scalar, so
  every block reads identically wherever it is placed. Only NOTE checks range.
- SPECTRUM has no frequency content. Bands are a fixed ratio of one number, so the hue
  never changes and only brightness moves.
- BEAT swallows the first transient. `runningAvg` is seeded with the first sample, so a
  lone impulse cannot exceed its own average.
- LEVEL decays twice per tick — `SoundEngine.tick()` and `processLevel` both subtract.

---

## Later

- **Console levels cannot drive the world.** Inbound Art-Net only emits an event carrying
  the first 8 channels; it never writes the DMX buffer feedback blocks read. A desk
  cannot push levels back into Minecraft.
- **Same-host Art-Net does not work.** UDP 6454 is contended, and the echo filter drops
  packets from any local address, so a console on the same machine is invisible. Needs a
  more precise self-check than "is this address local".
- **Duplicate registration across world loads.** Tick listeners and world hooks are
  registered inside `SERVER_STARTED`, which fires on every world load in a process, so
  they accumulate and old bridges keep running against dead engines.
- **sACN only transmits explicitly enabled universes.** Art-Net now follows whatever the
  engine drives; sACN still does not, so a block retargeted in game goes silent there.
- **`FixtureBlock` is misnamed.** `BlockRole` records that most blocks are inputs or
  triggers and only feedback blocks are fixtures. Renaming reaches through the mod and
  the save format, so it wants its own branch.
- **MQTT and MIDI are never started.** Both services exist but nothing instantiates them,
  so they are unreachable in game. MIDI inputs are reported as available, not connected.
- **README targets Minecraft 1.21.8.** Stale since the 26.2 port.

---

## Done

Grouped, most recent first. See git history for specifics.

- **Events over DMX.** Event ports carry an optional universe and channel and pulse it on
  the rising edge, so a desk can trigger on a block. Lua gained `dmx()` and `pulse()`,
  extending the same route to any game event.
- **Art-Net correctness.** Port-Address byte order fixed — the previous layout put every
  universe somewhere nobody was listening. Universes the engine drives are now
  transmitted whether or not they were enabled up front.
- **Web console as a monitor.** Blocks, sound blocks, protocol-server status with the
  peers talking to each one, and a live signal feed of everything reaching the engine.
  Inbound commands from every protocol are attributed to their source.
- **Console performance.** The feed sends only what changed and the page skips unchanged
  DOM writes, taking an idle console from about half a core to nothing.
- **Transport rewrite.** SSE replaced a WebSocket that had never delivered a single byte.
- **Block roles.** Blocks are classified input / trigger / fixture and grouped that way,
  rather than calling everything a fixture.
- **Port to Minecraft 26.2** — Mojang mappings, Java 25, new Loom, plus the in-game
  configuration screens and the fixes that followed.
