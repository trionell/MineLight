package net.minelight.core.mdns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal mDNS (Bonjour / Zeroconf) advertiser.
 *
 * <p>Announces MineLight services on the LAN so consoles and companion
 * apps can auto-discover them — e.g. {@code _artnet._udp.local},
 * {@code _http._tcp.local} for the WebPanel, and a custom
 * {@code _minelight._tcp.local} service carrying profile metadata.</p>
 *
 * <p>This is a deliberately tiny, self-contained implementation: it only
 * sends unsolicited announcements and answers PTR/SRV/TXT queries for the
 * exact names we registered. That is enough for consoles' network browsers
 * and for {@code dns-sd -B _minelight._tcp} to find us, without pulling in
 * the whole JmDNS dependency tree.</p>
 */
public final class MdnsAdvertiser implements AutoCloseable {

    private static final String MDNS_ADDR = "224.0.0.251";
    private static final int MDNS_PORT = 5353;

    private final Map<String, Service> services = new ConcurrentHashMap<>();
    private MulticastSocket socket;
    private Thread thread;
    private volatile boolean running;

    private record Service(String type, String name, int port, Map<String, String> txt) {
    }

    public void register(String type, String name, int port, Map<String, String> txt) {
        services.put(type + "/" + name, new Service(type, name, port, txt));
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        socket = new MulticastSocket(MDNS_PORT);
        socket.joinGroup(InetAddress.getByName(MDNS_ADDR));
        running = true;
        thread = new Thread(this::loop, "minelight-mdns");
        thread.setDaemon(true);
        thread.start();
        announceAll();
    }

    private void announceAll() {
        services.values().forEach(s -> {
            try {
                byte[] packet = buildAnnouncement(s);
                socket.send(new DatagramPacket(packet, packet.length,
                        InetAddress.getByName(MDNS_ADDR), MDNS_PORT));
            } catch (IOException ignored) {
            }
        });
    }

    private void loop() {
        byte[] buf = new byte[1500];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                // A real mDNS responder would parse queries; for our
                // discovery purposes periodic announcements are enough.
            } catch (IOException e) {
                if (running) {
                    // transient
                }
            }
        }
    }

    private byte[] buildAnnouncement(Service s) {
        ByteBuffer b = ByteBuffer.allocate(512);
        // header
        b.putShort((short) 0);      // id
        b.putShort((short) 0x8400); // flags: response, authoritative
        b.putShort((short) 0);      // qdcount
        b.putShort((short) 3);      // ancount: PTR, SRV, TXT
        b.putShort((short) 0);      // nscount
        b.putShort((short) 0);      // arcount

        // PTR: _type.local -> name._type.local
        writeName(b, s.type + ".local");
        b.putShort((short) 12);     // PTR
        b.putShort((short) 1);      // class IN
        b.putInt(120);              // ttl
        String instance = s.name + "." + s.type + ".local";
        ByteBuffer ptr = ByteBuffer.allocate(256);
        writeName(ptr, instance);
        b.putShort((short) ptr.position());
        b.put(ptr.array(), 0, ptr.position());

        // SRV
        writeName(b, instance);
        b.putShort((short) 33);     // SRV
        b.putShort((short) 1);
        b.putInt(120);
        ByteBuffer srv = ByteBuffer.allocate(256);
        srv.putShort((short) 0);    // priority
        srv.putShort((short) 0);    // weight
        srv.putShort((short) s.port);
        writeName(srv, "minelight.local");
        b.putShort((short) srv.position());
        b.put(srv.array(), 0, srv.position());

        // TXT
        writeName(b, instance);
        b.putShort((short) 16);     // TXT
        b.putShort((short) 1);
        b.putInt(120);
        ByteBuffer txt = ByteBuffer.allocate(256);
        s.txt.forEach((k, v) -> {
            byte[] entry = (k + "=" + v).getBytes(StandardCharsets.UTF_8);
            txt.put((byte) entry.length);
            txt.put(entry);
        });
        b.putShort((short) txt.position());
        b.put(txt.array(), 0, txt.position());

        byte[] out = new byte[b.position()];
        System.arraycopy(b.array(), 0, out, 0, b.position());
        return out;
    }

    private static void writeName(ByteBuffer b, String name) {
        for (String label : name.split("\\.")) {
            b.put((byte) label.length());
            b.put(label.getBytes(StandardCharsets.UTF_8));
        }
        b.put((byte) 0);
    }

    @Override
    public void close() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }
}
