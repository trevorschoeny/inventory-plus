package com.trevorschoeny.inventoryplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → Client: tells the client whether peek is open or closed.
 *
 * <p>When opening, includes the source type, active slot count, and item
 * display name so the client knows the layout and can render a title.
 * When closing, {@code slotIndex} is -1.
 */
public record PeekS2CPayload(
        int slotIndex,      // -1 = closed, else the peeked inventory slot
        int sourceType,     // 0=item_container, 1=bundle, 2=ender_chest
        int activeSlots,    // how many slots to show
        Component title     // display name for the panel header
) implements CustomPacketPayload {

    /** Source type constants */
    public static final int SOURCE_ITEM_CONTAINER = 0;
    public static final int SOURCE_BUNDLE = 1;
    public static final int SOURCE_ENDER_CHEST = 2;

    public static final Type<PeekS2CPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("inventory-plus", "peek_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PeekS2CPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PeekS2CPayload::slotIndex,
                    ByteBufCodecs.VAR_INT, PeekS2CPayload::sourceType,
                    ByteBufCodecs.VAR_INT, PeekS2CPayload::activeSlots,
                    ComponentSerialization.STREAM_CODEC, PeekS2CPayload::title,
                    PeekS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Convenience factory for a "closed" response. */
    public static PeekS2CPayload closed() {
        return new PeekS2CPayload(-1, 0, 0, Component.empty());
    }
}
