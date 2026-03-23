package com.trevorschoeny.inventoryplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → Server: "peek at the item in slot X" or "close peek" (-1).
 *
 * <p>The server validates the slot, determines the source type (shulker, bundle,
 * ender chest), binds the ephemeral container, and responds with {@link PeekS2CPayload}.
 */
public record PeekC2SPayload(int slotIndex) implements CustomPacketPayload {

    public static final Type<PeekC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("inventory-plus", "peek_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PeekC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PeekC2SPayload::slotIndex,
                    PeekC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
