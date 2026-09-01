package net.minelight.core.osc;

import net.minelight.core.api.GameEvent;
import net.minelight.core.api.PeerTracker;
import net.minelight.core.api.ProtocolServer;
import net.minelight.core.engine.ConsoleEngine;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OSC (Open Sound Control) server.
 *
 * <p>Listens for OSC messages under {@code /minelight/**} and emits them as
 * game events so scripts, TouchDesigner, QLC+, or a phone app can drive
 * Minecraft lighting:</p>
 *
 * <ul>
 *   <li>{@code /minelight/fixture/<id>/intensity <0-255>}</li>
 *   <li>{@code /minelight/fixture/<id>/set <v1> <v2> ...}</li>
 *   <li>{@code /minelight/preset/<name> <1>}</li>
 *   <li>{@code /minelight/cue/<list> <index>}</li>
 *   <li>{@code /minelight/event/<name> [args...]} — custom trigger</li>
 * </ul>
 */
public final class OscServer implements ProtocolServer {

    public static final int OSC_PORT = 8000;

    private final ConsoleEngine engine;
    private final int port;
    private final PeerTracker peers = new PeerTracker();

    private DatagramSocket socket;
    private Thread rxThread;
    private volatile boolean running;

    public OscServer(ConsoleEngine engine) {
        this(engine, OSC_PORT);
    }

    public OscServer(ConsoleEngine engine, int port) {
        this.engine = engine;
        this.port = port;
    }

    @Override
    public String name() {
        return "OSC";
    }

    @Override
    public int defaultPort() {
        return OSC_PORT;
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        socket = new DatagramSocket(port);
        running = true;
        rxThread = new Thread(this::rxLoop, "minelight-osc-rx");
        rxThread.setDaemon(true);
        rxThread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public com.google.gson.JsonObject status() {
        com.google.gson.JsonObject o = ProtocolServer.super.status();
        o.addProperty("port", port);
        o.add("peers", peers.toJson());
        return o;
    }

    private void rxLoop() {
        byte[] buf = new byte[2048];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                handle(p.getData(), p.getLength(), p.getAddress(), p.getPort());
            } catch (IOException e) {
                if (running) {
                    // transient
                }
            }
        }
    }

    // ---- OSC decoding (minimal) ------------------------------------------

    private void handle(byte[] data, int len, InetAddress from, int fromPort) {
        try {
            ByteBuffer b = ByteBuffer.wrap(data, 0, len);
            String address = readString(b);
            if (!address.startsWith("/minelight")) {
                return;
            }
            peers.seen(from, fromPort, address);
            String types = readString(b);
            if (!types.startsWith(",")) {
                return;
            }
            List<Object> args = new ArrayList<>();
            for (int i = 1; i < types.length(); i++) {
                char t = types.charAt(i);
                switch (t) {
                    case 'i' -> args.add(b.getInt());
                    case 'f' -> args.add(b.getFloat());
                    case 's' -> args.add(readString(b));
                    case 'T' -> args.add(Boolean.TRUE);
                    case 'F' -> args.add(Boolean.FALSE);
                    default -> {
                    }
                }
            }
            route(address, args);
        } catch (Exception ignored) {
            // malformed packet
        }
    }

    private static String readString(ByteBuffer b) {
        StringBuilder sb = new StringBuilder();
        byte c;
        while (b.hasRemaining() && (c = b.get()) != 0) {
            sb.append((char) c);
        }
        // 4-byte align
        int consumed = sb.length() + 1;
        int pad = (4 - (consumed % 4)) % 4;
        b.position(b.position() + pad);
        return sb.toString();
    }

    private void route(String address, List<Object> args) {
        String[] parts = address.split("/");
        // /minelight/<category>/...
        if (parts.length < 3) {
            return;
        }
        String category = parts[2];
        switch (category) {
            case "fixture" -> {
                if (parts.length < 5 || args.isEmpty()) {
                    return;
                }
                int id = Integer.parseInt(parts[3]);
                String action = parts[4];
                if ("intensity".equals(action)) {
                    engine.setFixtureIntensity(id, toInt(args.get(0)));
                } else if ("set".equals(action)) {
                    int[] levels = args.stream().mapToInt(this::toInt).toArray();
                    engine.setFixtureLevels(id, levels);
                }
            }
            case "preset" -> {
                if (parts.length >= 4) {
                    engine.applyPreset(parts[3]);
                }
            }
            case "cue" -> {
                if (parts.length >= 4) {
                    var cl = engine.cueList(parts[3]);
                    var cue = args.isEmpty() ? cl.next() : cl.go(toInt(args.get(0)));
                    if (cue != null && cue.levels() != null) {
                        cue.levels().forEach(engine::setFixtureLevels);
                    }
                }
            }
            case "event" -> {
                Map<String, Object> data = new HashMap<>();
                for (int i = 0; i < args.size(); i++) {
                    data.put("arg" + i, args.get(i));
                }
                String name = parts.length >= 4 ? parts[3] : "osc";
                engine.emit(new GameEvent("custom." + name, data));
            }
            default -> {
            }
        }
    }

    private int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(o));
    }

    // ---- OSC encoding (for feedback) -------------------------------------

    /** Build a simple OSC message with int args. */
    public static byte[] buildMessage(String address, int... values) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        writeString(out, address);
        StringBuilder types = new StringBuilder(",");
        for (int ignored : values) {
            types.append('i');
        }
        writeString(out, types.toString());
        ByteBuffer bb = ByteBuffer.allocate(values.length * 4);
        for (int v : values) {
            bb.putInt(v);
        }
        out.writeBytes(bb.array());
        return out.toByteArray();
    }

    private static void writeString(java.io.ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeBytes(bytes);
        out.write(0);
        int pad = (4 - ((bytes.length + 1) % 4)) % 4;
        for (int i = 0; i < pad; i++) {
            out.write(0);
        }
    }

    /** Send feedback to an OSC client. */
    public void send(InetAddress to, int toPort, String address, int... values) throws IOException {
        byte[] msg = buildMessage(address, values);
        socket.send(new DatagramPacket(msg, msg.length, to, toPort));
    }
}
