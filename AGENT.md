# AGENT.md

Working notes for agents and new contributors. Read [ROADMAP.md](ROADMAP.md) for what
is planned and what is done.

## Layout

- **`minelight-core`** — the lighting engine. Plain Java, **no Minecraft dependencies**,
  and the only module with tests. Patch, DMX merge, protocol servers (Art-Net, sACN,
  OSC, HTTP, MQTT), the Lua trigger engine, and the WebConsole.
- **`minelight-fabric`** — the Minecraft mod. Blocks, block entities, screens,
  networking, and the bridges that feed the engine each server tick.

Keep engine logic in core. Anything testable without Minecraft belongs there, and that
is what makes the project verifiable at all.

## Build and test

```bash
./gradlew :minelight-core:test        # 52 tests, the real safety net
./gradlew :minelight-fabric:build     # produces the installable jar
```

Gradle caches aggressively; add `--rerun-tasks` when you need a truthful test count.

## Environment

- **Minecraft 26.2**, **Java 25**, Fabric Loader 0.19.3, Fabric API `0.159.0+26.2`.
  Year-based versioning — there is no 1.26.x.
- **Mojang official mappings**, not Yarn. Yarn stopped after 1.21.11.
- Uses the newer `net.fabricmc.fabric-loom` plugin, not the old `fabric-loom`.

## Traps

Each of these has already cost real time. They are not obvious from the code.

- **The WebConsole speaks SSE, not WebSocket.** `com.sun.net.httpserver` completes a 101
  handshake and then silently discards the response body, so an upgraded connection
  never delivers a byte and the failure looks like an empty page. Server pushes go out
  on `/events`; the browser posts commands to `/command`.
- **The console only sends sections that changed.** Re-sending unchanged state makes the
  page rebuild DOM subtrees, which wakes every MutationObserver in the browser
  (extensions included) and burns CPU. On the client, every `innerHTML` write goes
  through one guard. Do not add a payload field that changes every tick — an age or a
  timestamp in a polled section defeats the whole mechanism.
- **Art-Net is mixed-endian.** The Port-Address is sent low byte first while the length
  two bytes later is high byte first. Getting it wrong still produces a packet consoles
  accept — they just file it under a universe nobody is listening to. `ArtNetWireTest`
  pins the byte layout.
- **Runtime dependencies must be `include`d, not just `implementation`.** `implementation`
  only reaches the compile classpath; anything the engine needs at runtime has to be
  nested into the mod jar or it fails with `NoClassDefFoundError` in game.
- **`ServerLifecycleEvents.SERVER_STARTED` runs on every world load** in the same
  process, and things registered inside it accumulate. See ROADMAP.

## Verifying

The Minecraft API cannot be recalled reliably from memory, and neither can wire formats.
Check them:

- Disassemble the actual jars with `javap` rather than guessing at signatures.
- For protocol work, decode the bytes the code really emits.
- For the console UI, the page can be rendered headless against a captured payload to
  catch runtime errors without a browser.

A test that pins observed behaviour is worth more than one that restates the code.

## Conventions

- **Conventional Commits.** One logical change per commit; explain *why* in the body when
  it is not obvious. Match the existing style — the history is fairly disciplined.
- **Do not commit or push unless asked.**
- Match the conventions of the file being edited over any general preference. Comment
  the *why*, not the *what*.
- Do not add README-style summary documents unless asked for.
