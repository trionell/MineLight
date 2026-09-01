package net.minelight.fabric.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minelight.fabric.blockentity.FixtureBlockEntity;
import net.minelight.fabric.blockentity.SoundBlockEntity;

/**
 * Config screens are client-side, but the engine and the saved NBT live on the
 * server, so every edit made in a GUI travels back as one of these payloads.
 */
public final class ModNetworking {

    /** Same reach limit the screens' stillValid checks use. */
    private static final double MAX_REACH_SQ = 64.0;

    private ModNetworking() {
    }

    /** Payload types must be known to both sides, so this runs from the main entrypoint. */
    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(PortUpdatePayload.TYPE, PortUpdatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SoundConfigPayload.TYPE, SoundConfigPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(PortUpdatePayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!inReach(player, payload.pos())) {
                        return;
                    }
                    if (player.level().getBlockEntity(payload.pos()) instanceof FixtureBlockEntity be) {
                        be.updatePort(payload.mapping().name(), payload.mapping());
                    }
                }));

        ServerPlayNetworking.registerGlobalReceiver(SoundConfigPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!inReach(player, payload.pos())) {
                        return;
                    }
                    if (player.level().getBlockEntity(payload.pos()) instanceof SoundBlockEntity be) {
                        be.applyConfig(payload.radius(), payload.universe(), payload.channel(),
                                payload.gain(), payload.decay(), payload.beatThreshold());
                    }
                }));
    }

    // A payload names its own block position, so never trust it without checking
    // the sender is actually standing next to that block.
    private static boolean inReach(ServerPlayer player, BlockPos pos) {
        return player.level().isLoaded(pos)
                && player.distanceToSqr(Vec3.atCenterOf(pos)) < MAX_REACH_SQ;
    }
}
