package net.minelight.fabric.block;

import net.minelight.core.api.FixtureBlock;

/**
 * RGB fixture block: three independent redstone inputs on three faces drive
 * the red, green, and blue channels of an RGB fixture group.
 *
 * <p>Convention: <b>north = red, east = green, west = blue</b> (configurable
 * per-block via the GUI). Build a tiny analog RGB mixer with three comparator
 * lines, or drive it from three levers for eight colours.</p>
 */
public final class RgbBlock extends FixtureBlockBase {

    public RgbBlock(Settings settings) {
        super(settings, FixtureBlock.Type.RGB);
    }
}
