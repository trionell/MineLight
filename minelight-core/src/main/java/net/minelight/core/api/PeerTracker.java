package net.minelight.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Remembers which remote endpoints a protocol server has heard from.
 *
 * <p>Art-Net, sACN, OSC and the HTTP API are all connectionless, so "is the
 * desk plugged in?" can only be answered by remembering who last spoke to
 * us. Each {@link ProtocolServer} keeps one of these and reports it through
 * {@link ProtocolServer#status()}; the WebConsole turns that into the
 * Devices panel.</p>
 *
 * <p>Entries never expire on their own — a peer that has gone quiet is still
 * worth showing, greyed out, with how long ago it was last heard. Callers
 * that want a hard cutoff pass one to {@link #toJson(long)}.</p>
 */
public final class PeerTracker {

    /** Cap on distinct peers remembered, so a scan cannot grow this forever. */
    private static final int MAX_PEERS = 64;

    /** One remote endpoint and what we last heard from it. */
    public static final class Peer {
        private final String address;
        private final AtomicLong packets = new AtomicLong();
        private volatile long lastSeen;
        private volatile String lastDetail = "";

        Peer(String address) {
            this.address = address;
        }

        public String address() {
            return address;
        }

        public long packets() {
            return packets.get();
        }

        public long lastSeen() {
            return lastSeen;
        }

        public String lastDetail() {
            return lastDetail;
        }
    }

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();

    /**
     * Record traffic from a peer.
     *
     * @param address remote address, already formatted for display
     * @param detail  what it just sent ("ArtDmx u1", "GET /api/dmx", ...)
     */
    public void seen(String address, String detail) {
        Peer p = peers.computeIfAbsent(address, Peer::new);
        p.packets.incrementAndGet();
        p.lastSeen = System.currentTimeMillis();
        p.lastDetail = detail == null ? "" : detail;
        if (peers.size() > MAX_PEERS) {
            evictOldest();
        }
    }

    public void seen(InetAddress address, int port, String detail) {
        seen(address.getHostAddress() + ":" + port, detail);
    }

    public List<Peer> all() {
        List<Peer> out = new ArrayList<>(peers.values());
        out.sort(Comparator.comparingLong(Peer::lastSeen).reversed());
        return out;
    }

    public int size() {
        return peers.size();
    }

    public void clear() {
        peers.clear();
    }

    /** All peers, newest first. */
    public JsonArray toJson() {
        return toJson(0);
    }

    /**
     * @param maxAgeMs drop peers not heard from in this long; 0 keeps all
     */
    public JsonArray toJson(long maxAgeMs) {
        long now = System.currentTimeMillis();
        JsonArray arr = new JsonArray();
        for (Peer p : all()) {
            long age = now - p.lastSeen;
            if (maxAgeMs > 0 && age > maxAgeMs) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("address", p.address());
            o.addProperty("packets", p.packets());
            // Absolute, not an age: a value that ticks every second would make
            // this payload differ on every poll, and monitors that only send
            // what changed would then never go quiet.
            o.addProperty("lastSeen", p.lastSeen());
            o.addProperty("detail", p.lastDetail());
            arr.add(o);
        }
        return arr;
    }

    private void evictOldest() {
        peers.values().stream()
                .min(Comparator.comparingLong(Peer::lastSeen))
                .ifPresent(oldest -> peers.remove(oldest.address()));
    }
}
