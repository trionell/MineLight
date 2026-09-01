package net.minelight.fabric.block;

import net.minelight.core.api.FixtureBlock;

/**
 * Event fixture block: a boolean redstone input; each rising edge
 * (unpowered → powered) fires a custom event at the console
 * (e.g. {@code custom.block4} or a named cue trigger).
 *
 * <p>Wire it to a pressure plate for a "player stepped here" lighting cue, a
 * daylight detector for a sunrise look, or a tripwire for a stage entrance.</p>
 */
public final class EventBlock extends FixtureBlockBase {

    public EventBlock(Settings settings) {
        super(settings, FixtureBlock.Type.EVENT);
    }
}
