package net.minelight.fabric.block;

import net.minelight.core.api.FixtureBlock;

/**
 * Dimmer fixture block: one redstone input; the strongest incoming signal
 * (0–15) scales to a DMX value 0–255 on the configured channel.
 *
 * <p>Feed it with a lever for on/off, a comparator for analog control, or a
 * redstone wire run for a distance-fade effect.</p>
 */
public final class DimmerBlock extends FixtureBlockBase {

    public DimmerBlock(Settings settings) {
        super(settings, FixtureBlock.Type.DIMMER);
    }
}
