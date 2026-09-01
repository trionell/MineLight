package net.minelight.fabric.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What the server hands the client when a sound fixture screen opens. */
public record SoundMenuData(BlockPos pos, int radius, int universe, int channel,
                            double gain, double decay, double beatThreshold) {

    public static final StreamCodec<RegistryFriendlyByteBuf, SoundMenuData> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SoundMenuData::pos,
                    ByteBufCodecs.VAR_INT, SoundMenuData::radius,
                    ByteBufCodecs.VAR_INT, SoundMenuData::universe,
                    ByteBufCodecs.VAR_INT, SoundMenuData::channel,
                    ByteBufCodecs.DOUBLE, SoundMenuData::gain,
                    ByteBufCodecs.DOUBLE, SoundMenuData::decay,
                    ByteBufCodecs.DOUBLE, SoundMenuData::beatThreshold,
                    SoundMenuData::new);
}
