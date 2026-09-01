package net.minelight.core.sacn;

import net.minelight.core.api.ProtocolServer;
import net.minelight.core.api.SacnOutput;
import net.minelight.core.engine.ConsoleEngine;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * sACN (ANSI E1.31) output.
 *
 * <p>Multicast per-universe output with configurable priority (0-200,
 * default 100) and HTP merge handled by the receiving console. GrandMA3,
 * Avolites Titan, and most modern nodes speak sACN natively.</p>
 */
public final class SacnServer implements ProtocolServer, SacnOutput {

    private static final int SACN_PORT = 5568;
    private static final byte[] ACN_PID = {0x41, 0x53, 0x43, 0x2d, 0x45, 0x31, 0x2e, 0x31, 0x37, 0, 0, 0};

    private final ConsoleEngine engine;
    private final Map<Integer, UniverseState> universes = new ConcurrentHashMap<>();

    private MulticastSocket socket;
    private ScheduledExecutorService tx;
    private volatile boolean running;

    private static final class UniverseState {
        volatile boolean enabled;
        volatile int priority = 100;
        volatile int sequence;
    }

    public SacnServer(ConsoleEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "sACN (E1.31)";
    }

    @Override
    public int defaultPort() {
        return SACN_PORT;
    }

    @Override
    public void enableUniverse(int universe) {
        universes.computeIfAbsent(universe, u -> new UniverseState()).enabled = true;
    }

    @Override
    public void disableUniverse(int universe) {
        UniverseState s = universes.get(universe);
        if (s != null) {
            s.enabled = false;
        }
    }

    @Override
    public void setPriority(int universe, int priority) {
        if (priority < 0 || priority > 200) {
            throw new IllegalArgumentException("sACN priority must be 0-200");
        }
        universes.computeIfAbsent(universe, u -> new UniverseState()).priority = priority;
    }

    @Override
    public boolean isEnabled(int universe) {
        UniverseState s = universes.get(universe);
        return s != null && s.enabled;
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        socket = new MulticastSocket();
        running = true;
        tx = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "minelight-sacn-tx");
            t.setDaemon(true);
            return t;
        });
        tx.scheduleAtFixedRate(this::sendAll, 0, 25, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (tx != null) {
            tx.shutdownNow();
        }
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void sendAll() {
        if (!running) {
            return;
        }
        Map<Integer, byte[]> snap = engine.dmxSnapshot();
        universes.forEach((u, state) -> {
            if (!state.enabled) {
                return;
            }
            byte[] dmx = snap.getOrDefault(u, new byte[512]);
            try {
                byte[] packet = buildPacket(u, dmx, state);
                InetAddress group = InetAddress.getByName(multicastAddress(u));
                socket.send(new DatagramPacket(packet, packet.length, group, SACN_PORT));
                state.sequence = (state.sequence + 1) & 0xFF;
            } catch (IOException ignored) {
            }
        });
    }

    private static String multicastAddress(int universe) {
        return "239.255." + ((universe >> 8) & 0xFF) + "." + (universe & 0xFF);
    }

    private byte[] buildPacket(int universe, byte[] dmx, UniverseState state) {
        ByteBuffer b = ByteBuffer.allocate(126 + dmx.length);

        // Root layer
        b.putShort((short) 0x0010);                       // Preamble size
        b.putShort((short) 0x0000);                       // Postamble size
        b.put(ACN_PID);                                    // ACN packet identifier
        b.putShort((short) (0x7000 | (110 + dmx.length))); // Flags + length
        b.putInt(0x00000004);                              // Vector: VECTOR_E131_DATA_PACKET
        b.put("MineLight".getBytes(java.nio.charset.StandardCharsets.UTF_8)); // CID (16 bytes, padded)
        b.put(new byte[16 - 9]);

        // Framing layer
        b.putShort((short) (0x7000 | (88 + dmx.length)));
        b.putInt(0x00000002);                              // VECTOR_E131_DATA_PACKET framing
        byte[] source = new byte[64];
        byte[] nm = "MineLight".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(nm, 0, source, 0, nm.length);
        b.put(source);
        b.put((byte) state.priority);
        b.putShort((short) 0);                             // sync address
        b.put((byte) state.sequence);
        b.put((byte) 0);                                   // options
        b.putShort((short) universe);

        // DMP layer
        b.putShort((short) (0x7000 | (11 + dmx.length)));
        b.put((byte) 0x02);                                // VECTOR_DMP_SET_PROPERTY
        b.put((byte) 0xa1);                                // address type & data type
        b.putShort((short) 0x0000);                        // first property address
        b.putShort((short) 0x0001);                        // address increment
        b.putShort((short) (1 + dmx.length));              // value length
        b.put((byte) 0);                                   // start code
        b.put(dmx);

        return b.array();
    }
}
