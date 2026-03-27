package com.trevorschoeny.inventoryplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> Server: "toggle sort-lock on a specific slot."
 *
 * <p>Carries the menu slot index and the desired sort-lock state so the
 * server can set it on the corresponding Slot in
 * {@link com.trevorschoeny.menukit.MKSlotStateRegistry}. Without this
 * packet, sort-lock state only exists client-side and sorting / shift-click
 * routing (which run server-side) never see it.
 */
public record SortLockC2SPayload(int slotIndex, boolean locked) implements CustomPacketPayload {

    public static final Type<SortLockC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("inventory-plus", "sort_lock_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SortLockC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SortLockC2SPayload::slotIndex,
                    ByteBufCodecs.BOOL, SortLockC2SPayload::locked,
                    SortLockC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
