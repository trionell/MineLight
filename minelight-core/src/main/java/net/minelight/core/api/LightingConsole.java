package net.minelight.core.api;

/**
 * A connected lighting console as seen by the engine — a source of control
 * (faders, buttons, commands) and a sink of feedback (fixture levels,
 * redstone state readback).
 */
public interface LightingConsole {

    /** Console family: "grandma2", "grandma3", "avolites-titan", "webpanel", "generic". */
    String family();

    /** Console version string if known, else "unknown". */
    String version();

    /** Whether the console supports receiving redstone-state feedback. */
    boolean supportsFeedback();

    /**
     * Push a feedback value to the console (e.g. a redstone lamp is now on).
     * No-op if {@link #supportsFeedback()} is false.
     */
    default void feedback(String target, int value) {
    }
}
