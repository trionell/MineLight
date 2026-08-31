package net.minelight.core.api;

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
}
