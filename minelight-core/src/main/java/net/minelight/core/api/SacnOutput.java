package net.minelight.core.api;

/**
 * Marker contract for sACN (E1.31) output capability. Implemented by the
 * engine's sACN module; exposed so backends can require sACN support.
 */
public interface SacnOutput {

    /** Enable sACN output for the given universe. */
    void enableUniverse(int universe);

    /** Disable sACN output for the given universe. */
    void disableUniverse(int universe);

    /** Set per-universe sACN priority (0-200, default 100). */
    void setPriority(int universe, int priority);

    /** Whether sACN output is currently enabled for this universe. */
    boolean isEnabled(int universe);
}
