package net.minelight.fabric.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minelight.core.api.FixtureBlock;

import java.util.List;
import java.util.Optional;

/**
 * What the server hands the client when a fixture screen opens.
 *
 * <p>The client has no access to the engine, so the authoritative port list
 * travels with the open-screen packet rather than being looked up locally.</p>
 */
public record FixtureMenuData(BlockPos pos, List<FixtureBlock.PortMapping> ports) {

    /** Shared by the menu payload and the client's edit packets. */
    public static final StreamCodec<RegistryFriendlyByteBuf, FixtureBlock.PortMapping> PORT_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FixtureBlock.PortMapping::name,
                    ByteBufCodecs.STRING_UTF8, p -> p.side().name(),
                    ByteBufCodecs.STRING_UTF8, p -> p.action().name(),
                    ByteBufCodecs.VAR_INT, FixtureBlock.PortMapping::universe,
                    ByteBufCodecs.VAR_INT, FixtureBlock.PortMapping::channel,
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), p -> Optional.ofNullable(p.event()),
                    (name, side, action, universe, channel, event) -> new FixtureBlock.PortMapping(
                            name,
                            FixtureBlock.Side.valueOf(side),
                            FixtureBlock.Action.valueOf(action),
                            universe, channel, event.orElse(null)));

    public static final StreamCodec<RegistryFriendlyByteBuf, FixtureMenuData> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, FixtureMenuData::pos,
                    PORT_CODEC.apply(ByteBufCodecs.list()), FixtureMenuData::ports,
                    FixtureMenuData::new);
}
