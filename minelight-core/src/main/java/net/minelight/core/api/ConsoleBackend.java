package net.minelight.core.api;

/**
 * A console-specific adapter sitting behind one or more {@link ProtocolServer}s.
 *
 * <p>Where a {@code ProtocolServer} speaks a wire protocol (Art-Net, OSC...),
 * a {@code ConsoleBackend} knows about a particular console family's quirks:
 * GrandMA2 vs GrandMA3 vs Avolites Titan naming, fixture-library export
 * formats, RDM-handshakes, session protocols (MA-Net), and so on.</p>
 *
 * <p>Backends are optional. The built-in WebPanel and generic Art-Net/sACN
 * output need no backend; you add one when you want console-specific
 * polish like "Export .xml fixture library for MA2".</p>
 */
public interface ConsoleBackend {

    /** Stable id, e.g. "grandma2", "grandma3", "avolites-titan". */
    String id();

    /** Human-readable name. */
    String displayName();

    /**
     * Export the patch in the console's native fixture-library format, or
     * {@code null} if this console consumes generic Art-Net/sACN only.
     */
    default String exportFixtureLibrary(Patch patch) {
        return null;
    }

    /** Called when the engine starts; hook console-specific session setup. */
    default void onEngineStart(ConsoleEngineContext ctx) {
    }

    /** Called when the engine stops. */
    default void onEngineStop() {
    }
}
