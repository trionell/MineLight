package net.minelight.fabric.block;

import net.minelight.core.api.FixtureBlock;

/**
 * Feedback fixture block: the reverse of the others — it <em>receives</em> a
 * level from the console and emits redstone power proportional to it (0–15).
 *
 * <p>Use it to drive in-game contraptions from the lighting desk: a fader at
 * 50% powers a piston halfway, a cue at full opens a door, a blackout kills a
 * beacon beam.</p>
 */
public final class FeedbackBlock extends FixtureBlockBase {

    public FeedbackBlock(Settings settings) {
        super(settings, FixtureBlock.Type.FEEDBACK);
    }
}
