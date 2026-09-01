package net.minelight.fabric.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minelight.fabric.MineLightMod;

/** Client -> server: apply edited sound fixture config to the block at {@code pos}. */
public record SoundConfigPayload(BlockPos pos, int radius, int universe, int channel,
                                 double gain, double decay, double beatThreshold)
        implements CustomPacketPayload {

    public static final Type<SoundConfigPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "sound_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoundConfigPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SoundConfigPayload::pos,
                    ByteBufCodecs.VAR_INT, SoundConfigPayload::radius,
                    ByteBufCodecs.VAR_INT, SoundConfigPayload::universe,
                    ByteBufCodecs.VAR_INT, SoundConfigPayload::channel,
                    ByteBufCodecs.DOUBLE, SoundConfigPayload::gain,
                    ByteBufCodecs.DOUBLE, SoundConfigPayload::decay,
                    ByteBufCodecs.DOUBLE, SoundConfigPayload::beatThreshold,
                    SoundConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
