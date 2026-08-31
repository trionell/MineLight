package net.minelight.core.api;

/**
 * Context handed to backends and protocol servers so they can talk back to
 * the engine without a direct dependency on the concrete engine class.
 */
public interface ConsoleEngineContext {

    /** The current patch. */
    Patch patch();

    /** Emit a game event into the trigger engine. */
    void emit(GameEvent event);

    /** Current merged DMX output: universe -> 512-channel array. */
    java.util.Map<Integer, byte[]> dmxSnapshot();

    /** Register a listener for merged DMX output changes. */
    void addDmxListener(DmxListener listener);

    @FunctionalInterface
    interface DmxListener {
        /** Called ~44 Hz (DMX rate) with the merged universe map. */
        void onDmx(java.util.Map<Integer, byte[]> universes);
    }
}
