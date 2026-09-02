package net.minelight.core.api;

/**
 * What a placed block does in the signal flow.
 *
 * <p>Most MineLight blocks are not fixtures in the lighting sense — they do
 * not receive a level from a console and turn it into light. They feed the
 * console: a dimmer block converts redstone strength into a DMX value, a
 * sound meter converts world audio into one. Only the feedback block runs the
 * other way, taking a console level and emitting redstone, which is the one
 * that behaves like a fixture on a desk.</p>
 */
public enum BlockRole {

    /** Drives a DMX channel from something in the world. */
    INPUT("Input"),

    /** Fires an event on an edge rather than tracking a level. */
    TRIGGER("Trigger"),

    /** Receives a level from the console and acts on it in the world. */
    FIXTURE("Fixture");

    private final String label;

    BlockRole(String label) {
        this.label = label;
    }

    /** Display name for consoles and monitors. */
    public String label() {
        return label;
    }
}
