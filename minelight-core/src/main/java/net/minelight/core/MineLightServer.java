package net.minelight.core;

import net.minelight.core.api.Patch;
import net.minelight.core.artnet.ArtNetServer;
import net.minelight.core.engine.ConsoleEngine;
import net.minelight.core.http.HttpApiServer;
import net.minelight.core.mdns.MdnsAdvertiser;
import net.minelight.core.osc.OscServer;
import net.minelight.core.sacn.SacnServer;
import net.minelight.core.webconsole.WebConsoleServer;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Headless MineLight server — runs the full lighting engine without Minecraft.
 *
 * <p>Starts every protocol server (Art-Net, sACN, OSC, HTTP API, WebConsole)
 * plus mDNS discovery, loads a demo patch on first run, and blocks until
 * Ctrl+C. Useful for building a show file on a laptop before pointing a real
 * console at a Minecraft server, or for running a standalone lighting bridge
 * for stream overlays / home automation.</p>
 *
 * <pre>
 *   gradle :minelight-core:run
 *   # or with options:
 *   gradle :minelight-core:run --args="--web-port 9090 --config ./myshow"
 * </pre>
 *
 * Options:
 *   --config &lt;dir&gt;       config directory (default: ./run/minelight)
 *   --web-port &lt;port&gt;    WebConsole port (default 8090)
 *   --http-port &lt;port&gt;   HTTP API port (default 8080)
 *   --osc-port &lt;port&gt;    OSC listen port (default 8000)
 *   --no-artnet            disable Art-Net
 *   --no-sacn              disable sACN
 *   --no-mdns              disable mDNS discovery
 *   --demo                 force-load the demo patch even if config exists
 */
public final class MineLightServer {

    private MineLightServer() {
    }

    public static void main(String[] args) throws Exception {
        Path configDir = Path.of("run", "minelight");
        int webPort = WebConsoleServer.WEB_PORT;
        int httpPort = HttpApiServer.HTTP_PORT;
        int oscPort = OscServer.OSC_PORT;
        boolean artnet = true;
        boolean sacn = true;
        boolean mdns = true;
        boolean demo = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configDir = Path.of(args[++i]);
                case "--web-port" -> webPort = Integer.parseInt(args[++i]);
                case "--http-port" -> httpPort = Integer.parseInt(args[++i]);
                case "--osc-port" -> oscPort = Integer.parseInt(args[++i]);
                case "--no-artnet" -> artnet = false;
                case "--no-sacn" -> sacn = false;
                case "--no-mdns" -> mdns = false;
                case "--demo" -> demo = true;
                default -> {
                    System.err.println("[MineLight] Unknown option: " + args[i]);
                    System.exit(2);
                }
            }
        }

        ConsoleEngine engine = new ConsoleEngine(configDir);

        if (artnet) {
            ArtNetServer art = new ArtNetServer(engine);
            art.enableUniverse(1);
            engine.registerServer(art);
        }
        if (sacn) {
            SacnServer sacnServer = new SacnServer(engine);
            sacnServer.enableUniverse(1);
            engine.registerServer(sacnServer);
        }
        engine.registerServer(new OscServer(engine, oscPort));
        engine.registerServer(new HttpApiServer(engine, httpPort));
        engine.registerServer(new WebConsoleServer(engine, webPort));

        engine.start();

        if (demo || engine.patch().size() == 0) {
            loadDemoPatch(engine);
        }

        MdnsAdvertiser advertiser = null;
        if (mdns) {
            advertiser = new MdnsAdvertiser();
            advertiser.register("_artnet._udp", "MineLight", 6454, Map.of("type", "artnet"));
            advertiser.register("_http._tcp", "MineLight WebConsole", webPort,
                    Map.of("path", "/", "type", "minelight-webconsole"));
            advertiser.start();
        }

        System.out.println("[MineLight] Engine started with " + engine.patch().size() + " fixture(s).");
        System.out.println("[MineLight] WebConsole:  http://localhost:" + webPort + "/");
        System.out.println("[MineLight] HTTP API:    http://localhost:" + httpPort + "/api/status");
        System.out.println("[MineLight] OSC:         udp/" + oscPort + "  (/minelight/**)");
        System.out.println("[MineLight] Press Ctrl+C to stop.");

        CountDownLatch shutdown = new CountDownLatch(1);
        MdnsAdvertiser advertiserRef = advertiser;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[MineLight] Shutting down…");
            if (advertiserRef != null) {
                advertiserRef.close();
            }
            engine.stop();
            shutdown.countDown();
        }, "minelight-shutdown"));
        shutdown.await();
    }

    /** A small demo rig so a fresh server has something to play with. */
    private static void loadDemoPatch(ConsoleEngine engine) {
        engine.patch().addFixture("Stage Left", Patch.RGB_DIMMER, 1, 1, 0, 64, 0, "lamp", true);
        engine.patch().addFixture("Stage Right", Patch.RGB_DIMMER, 1, 5, 4, 64, 0, "lamp", true);
        engine.patch().addFixture("Backdrop", Patch.STROBE, 1, 9, 2, 70, 8, "strobe");
        engine.patch().addFixture("House", Patch.DIMMER, 1, 11, -8, 64, -8, "house", true);
        engine.save();
        System.out.println("[MineLight] Loaded demo patch (4 fixtures).");
    }
}
