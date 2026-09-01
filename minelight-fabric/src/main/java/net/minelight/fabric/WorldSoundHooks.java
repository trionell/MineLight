package net.minelight.fabric;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionResult;

/**
 * Level-event hooks that feed the {@link SoundBridge}.
 *
 * <p>Note blocks are the precise, melodic input: when a player (or redstone)
 * plays a note block we forward pitch + instrument. Explosions, pistons and
 * other loud world events bump the ambient level for LEVEL/BEAT/SPECTRUM
 * fixtures.</p>
 */
public final class WorldSoundHooks {

    private WorldSoundHooks() {
    }

    public static void register(SoundBridge bridge) {
        // Player punches a note block -> it plays a note.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            var state = world.getBlockState(pos);
            if (state.getBlock() == Blocks.NOTE_BLOCK) {
                int note = state.getValueOrElse(BlockStateProperties.NOTE, 0);
                String instrument = state.hasProperty(BlockStateProperties.NOTEBLOCK_INSTRUMENT)
                        ? state.getValue(BlockStateProperties.NOTEBLOCK_INSTRUMENT).name().toLowerCase()
                        : "harp";
                bridge.onNoteBlockPlayed(pos.getX(), pos.getY(), pos.getZ(), note, instrument);
            }
            return InteractionResult.PASS;
        });

        // Loud world events bump the ambient level. We hook a few common ones
        // via Fabric's world events; a full implementation would also listen
        // for explosion and piston callbacks.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // a primed TNT / creeper about to blow is loud
            if (entity instanceof net.minecraft.world.entity.item.PrimedTnt) {
                bridge.bump(0.5);
            }
        });
    }
}
