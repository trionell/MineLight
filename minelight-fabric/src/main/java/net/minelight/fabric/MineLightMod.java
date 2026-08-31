package net.minelight.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minelight.core.artnet.ArtNetServer;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.core.http.HttpApiServer;
import net.minelight.core.mdns.MdnsAdvertiser;
import net.minelight.core.osc.OscServer;
import net.minelight.core.sacn.SacnServer;
import net.minelight.core.webconsole.WebConsoleServer;
import net.minelight.fabric.block.ModBlocks;
import net.minelight.fabric.blockentity.ModBlockEntities;
import net.minelight.fabric.screen.ModScreenHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/**
 * MineLight server-side entrypoint.
 *
 * <p>Boots the console-agnostic {@link ConsoleEngine}, registers all
 * protocol servers, hooks redstone polling, and exposes {@code /ml}
 * commands. The engine runs on both dedicated servers and the integrated
 * server (single-player / open-to-LAN).</p>
 */
public final class MineLightMod implements ModInitializer {

    public static final String MOD_ID = "minelight";
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLight");

    private static ConsoleEngine engine;
    private static RedstoneBridge redstoneBridge;
    private static FixtureBlockBridge fixtureBlockBridge;
    private static SoundBridge soundBridge;
    private static MdnsAdvertiser mdns;

    public static SoundBridge soundBridge() {
        return soundBridge;
    }

    public static ConsoleEngine engine() {
        return engine;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[MineLight] Initializing");

        // blocks, block entities, screens
        ModBlocks.register();
        ModBlockEntities.register();
        ModScreenHandlers.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MLCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Path configDir = server.getRunDirectory().toPath().resolve("config").resolve("minelight");
                engine = new ConsoleEngine(configDir);

                // Protocol servers — all optional, all console-agnostic
                ArtNetServer artnet = new ArtNetServer(engine);
                artnet.enableUniverse(1);
                engine.registerServer(artnet);

                SacnServer sacn = new SacnServer(engine);
                sacn.enableUniverse(1);
                engine.registerServer(sacn);

                engine.registerServer(new OscServer(engine));
                engine.registerServer(new HttpApiServer(engine));
                engine.registerServer(new WebConsoleServer(engine));

                engine.start();

                // mDNS discovery
                mdns = new MdnsAdvertiser();
                mdns.register("_artnet._udp", "MineLight", 6454, Map.of("type", "artnet"));
                mdns.register("_http._tcp", "MineLight WebConsole", 8090,
                        Map.of("path", "/", "type", "minelight-webconsole"));
                mdns.start();

                // redstone bridge polls the world each tick
                redstoneBridge = new RedstoneBridge(engine);
                ServerTickEvents.END_SERVER_TICK.register(redstoneBridge::tick);

                // fixture blocks: redstone in -> DMX out, console levels -> redstone out
                fixtureBlockBridge = new FixtureBlockBridge(engine);
                ServerTickEvents.END_SERVER_TICK.register(fixtureBlockBridge::tick);

                // sound-to-light: note blocks + ambient level -> DMX
                soundBridge = new SoundBridge(engine);
                ServerTickEvents.END_SERVER_TICK.register(soundBridge::tick);
                WorldSoundHooks.register(soundBridge);

                LOGGER.info("[MineLight] Engine started. WebConsole: http://localhost:8090/");
            } catch (Exception e) {
                LOGGER.error("[MineLight] Failed to start engine", e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (mdns != null) {
                mdns.close();
                mdns = null;
            }
            if (engine != null) {
                engine.stop();
                engine = null;
            }
        });
    }
}
