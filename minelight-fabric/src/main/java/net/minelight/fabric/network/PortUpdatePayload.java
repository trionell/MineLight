package net.minelight.fabric.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minelight.fabric.MineLightMod;
import net.minelight.core.api.FixtureBlock;
import net.minelight.fabric.screen.FixtureMenuData;

/** Client -> server: apply one edited port mapping to the fixture block at {@code pos}. */
public record PortUpdatePayload(BlockPos pos, FixtureBlock.PortMapping mapping) implements CustomPacketPayload {

    public static final Type<PortUpdatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(MineLightMod.MOD_ID, "port_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortUpdatePayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PortUpdatePayload::pos,
                    FixtureMenuData.PORT_CODEC, PortUpdatePayload::mapping,
                    PortUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
