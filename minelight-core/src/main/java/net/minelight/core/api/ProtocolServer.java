package net.minelight.core.api;

import com.google.gson.JsonObject;

/**
 * A network-facing protocol endpoint (Art-Net, sACN, OSC, HTTP, MQTT, ...).
 *
 * <p>Protocol servers are started and stopped with the engine and are
 * console-agnostic: they translate between MineLight's internal event/DMX
 * model and a wire protocol. Multiple protocol servers can run at once, so
 * you can e.g. output sACN to a GrandMA while a WebPanel shows live levels
 * and OSC feeds a TouchDesigner rig.</p>
 */
public interface ProtocolServer {

    /** Human-readable name, e.g. "Art-Net", "sACN", "WebConsole". */
    String name();

    /** Default port this protocol listens on (informational). */
    int defaultPort();

    /** Start the server. Idempotent. */
    void start() throws Exception;

    /** Stop the server. Idempotent. */
    void stop();

    boolean isRunning();

    /**
     * A live status snapshot for monitors (the WebConsole Devices panel).
     *
     * <p>The base fields are the same for every server; implementations
     * override this to add what only they can know — enabled universes,
     * {@link PeerTracker} entries for the desks that have spoken to them,
     * open MIDI ports, and so on.</p>
     */
    default JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name());
        o.addProperty("port", defaultPort());
        o.addProperty("running", isRunning());
        return o;
    }
}
