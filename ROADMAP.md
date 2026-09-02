# Roadmap

Planned and finished work. Update it as part of the change, not afterwards: move an item
when the work lands, and add newly discovered defects as you find them.

Git history holds the detail — keep entries here short enough to scan.

---

## Next

### Console levels cannot drive the world

Inbound Art-Net only emits an event carrying the first 8 channels; it never writes the
DMX buffer feedback blocks read. A desk cannot push levels back into Minecraft.

---

## Later

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
- **A replaced sound block keeps the old mode.** `SoundBlockEntity.registerWithEngine`
  reuses any engine block at the same position whatever its mode, so putting a spectrum
  block where a meter used to stand leaves it behaving as a meter. Seen while testing
  positional mixing with `/setblock`.
- **Sound blocks ignore dimension.** A block only stores x/y/z, so a meter in the Nether
  hears an explosion at the same coordinates in the Overworld. Found while building
  positional mixing.
- **Sounds sent as level events are missed.** `playSeededSound` and `explode` cover
  nearly everything, but jukebox records and a few block interactions travel as
  `levelEvent` ids the client turns into sound, so they never reach a sound block. A
  jukebox driving the lights is the obvious one to want.
- **README targets Minecraft 1.21.8.** Stale since the 26.2 port.

---

## Done

Grouped, most recent first. See git history for specifics.

- **Sound blocks hear the world.** The mod's first mixin taps
  `ServerLevel.playSeededSound`, the funnel every gameplay sound passes through, plus
  `explode` — an explosion's sound rides inside its own packet and would otherwise be
  the one thing a sound block could not hear. Each block is now a microphone at its own
  position: sounds are attenuated over their own audible range, gated by the block's
  radius, and summed as power per tick, so radius finally means something and two blocks
  in different rooms read differently. Frequency is estimated from the sound's name and
  pitch, which gives SPECTRUM real hue movement, and note blocks reach NOTE fixtures
  however they were triggered rather than only when punched. The old stub — one global
  scalar bumped by lit TNT — is gone, along with BEAT swallowing the first transient and
  LEVEL decaying twice per tick.
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
