package net.minelight.core.artnet;

import net.minelight.core.api.ProtocolServer;
import net.minelight.core.engine.ConsoleEngine;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Art-Net v4 output + input node.
 *
 * <p><b>Output:</b> broadcasts ArtDmx packets for every enabled universe at
 * DMX rate (~44 Hz) so consoles and nodes (GrandMA onPC, Avolites Titan with
 * Art-Net input, ELM, MadMapper, ENTTEC nodes...) can pick up MineLight
 * levels.</p>
 *
 * <p><b>Input:</b> listens for ArtDmx packets so a real console can drive
 * Minecraft fixtures. Incoming values are exposed to the trigger engine as
 * {@code artnet.dmx} events.</p>
 *
 * <p>Also answers ArtPoll with an ArtPollReply so the node shows up in
 * console network browsers ("MineLight Node").</p>
 */
public final class ArtNetServer implements ProtocolServer, ConsoleEngine.DmxListener {

    public static final int ARTNET_PORT = 6454;
    private static final byte[] ARTNET_HEADER = {'A', 'r', 't', '-', 'N', 'e', 't', 0};
    private static final int OP_POLL = 0x2000;
    private static final int OP_POLL_REPLY = 0x2100;
    private static final int OP_DMX = 0x5000;

    private final ConsoleEngine engine;
    private final String bindAddress;
    private final boolean inputEnabled;
    private final java.util.Set<Integer> outputUniverses = ConcurrentHashMap.newKeySet();

    private DatagramSocket socket;
    private ScheduledExecutorService tx;
    private Thread rxThread;
    private volatile boolean running;

    public ArtNetServer(ConsoleEngine engine) {
        this(engine, "0.0.0.0", true);
    }

    public ArtNetServer(ConsoleEngine engine, String bindAddress, boolean inputEnabled) {
        this.engine = engine;
        this.bindAddress = bindAddress;
        this.inputEnabled = inputEnabled;
    }

    @Override
    public String name() {
        return "Art-Net";
    }

    @Override
    public int defaultPort() {
        return ARTNET_PORT;
    }

    public void enableUniverse(int u) {
        outputUniverses.add(u);
    }

    public void disableUniverse(int u) {
        outputUniverses.remove(u);
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        socket = new DatagramSocket(ARTNET_PORT, InetAddress.getByName(bindAddress));
        socket.setBroadcast(true);
        running = true;

        engine.addDmxListener(this);

        if (inputEnabled) {
            rxThread = new Thread(this::rxLoop, "minelight-artnet-rx");
            rxThread.setDaemon(true);
            rxThread.start();
        }

        tx = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "minelight-artnet-tx");
            t.setDaemon(true);
            return t;
        });
        tx.scheduleAtFixedRate(this::sendDmx, 0, 25, TimeUnit.MILLISECONDS);
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

    @Override
    public void onDmx(Map<Integer, byte[]> universes) {
        // DMX pump is handled by the scheduled sender; listener keeps engine
        // from garbage-collecting us.
    }

    private void sendDmx() {
        if (!running) {
            return;
        }
        Map<Integer, byte[]> snap = engine.dmxSnapshot();
        for (int universe : outputUniverses) {
            byte[] data = snap.get(universe);
            if (data == null) {
                data = new byte[512];
            }
            try {
                byte[] packet = buildArtDmx(universe, data);
                DatagramPacket dp = new DatagramPacket(packet, packet.length,
                        InetAddress.getByName("255.255.255.255"), ARTNET_PORT);
                socket.send(dp);
            } catch (IOException ignored) {
            }
        }
    }

    private byte[] buildArtDmx(int universe, byte[] dmx) {
        ByteBuffer b = ByteBuffer.allocate(18 + dmx.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(ARTNET_HEADER);
        b.putShort((short) OP_DMX);          // OpCode (little endian on wire)
        b.order(ByteOrder.BIG_ENDIAN);
        b.putShort((short) 14);              // Protocol version
        b.put((byte) 0);                     // Sequence (0 = disabled)
        b.put((byte) 0);                     // Physical
        b.putShort((short) (universe & 0x7FFF)); // Port-Address
        b.putShort((short) dmx.length);      // Length (big endian)
        b.put(dmx);
        return b.array();
    }

    // ---- input ---------------------------------------------------------

    private void rxLoop() {
        byte[] buf = new byte[1024];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                handlePacket(p.getData(), p.getLength(), p.getAddress(), p.getPort());
            } catch (IOException e) {
                if (running) {
                    // transient
                }
            }
        }
    }

    private void handlePacket(byte[] data, int len, InetAddress from, int fromPort) throws IOException {
        if (len < 12) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            if (data[i] != ARTNET_HEADER[i]) {
                return;
            }
        }
        int opcode = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
        switch (opcode) {
            case OP_POLL -> sendPollReply(from, fromPort);
            case OP_DMX -> handleDmx(data, len);
            default -> {
            }
        }
    }

    private void handleDmx(byte[] data, int len) {
        if (len < 18) {
            return;
        }
        int universe = ((data[14] & 0xFF) | ((data[15] & 0xFF) << 8)) & 0x7FFF;
        int dmxLen = ((data[16] & 0xFF) << 8) | (data[17] & 0xFF);
        dmxLen = Math.min(dmxLen, Math.min(512, len - 18));
        Map<String, Object> ev = new java.util.HashMap<>();
        ev.put("universe", universe);
        // pass first few channels for scripting convenience
        for (int i = 0; i < Math.min(8, dmxLen); i++) {
            ev.put("ch" + (i + 1), data[18 + i] & 0xFF);
        }
        engine.emit(new net.minelight.core.api.GameEvent("artnet.dmx", ev));
    }

    private void sendPollReply(InetAddress to, int port) throws IOException {
        byte[] reply = new byte[239];
        System.arraycopy(ARTNET_HEADER, 0, reply, 0, 8);
        reply[8] = (byte) (OP_POLL_REPLY & 0xFF);
        reply[9] = (byte) (OP_POLL_REPLY >> 8);
        // my IP
        try {
            byte[] ip = InetAddress.getLocalHost().getAddress();
            System.arraycopy(ip, 0, reply, 10, 4);
        } catch (Exception ignored) {
        }
        reply[14] = (byte) (ARTNET_PORT & 0xFF);
        reply[15] = (byte) (ARTNET_PORT >> 8);
        reply[16] = 0; reply[17] = 14; // firmware
        byte[] shortName = "MineLight Node".getBytes();
        System.arraycopy(shortName, 0, reply, 26, Math.min(shortName.length, 17));
        byte[] longName = "MineLight Art-Net Node (Minecraft lighting bridge)".getBytes();
        System.arraycopy(longName, 0, reply, 44, Math.min(longName.length, 63));
        DatagramPacket dp = new DatagramPacket(reply, reply.length, to, port);
        socket.send(dp);
    }
}
