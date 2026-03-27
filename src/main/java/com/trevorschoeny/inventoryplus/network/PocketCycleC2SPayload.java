package com.trevorschoeny.inventoryplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> Server: "cycle my pocket for this hotbar slot."
 *
 * <p>Sent when the player presses a pocket-cycle keybind. The server
 * rotates items through the hotbar slot's pocket storage, respecting
 * disabled slots and the configured pocket size.
 *
 * <p>Two fields:
 * <ul>
 *   <li>{@code hotbarSlot} — the hotbar slot index (0-8) to cycle</li>
 *   <li>{@code forward} — true for forward rotation, false for backward</li>
 * </ul>
 */
public record PocketCycleC2SPayload(int hotbarSlot, boolean forward)
        implements CustomPacketPayload {

    public static final Type<PocketCycleC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("inventory-plus", "pocket_cycle_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketCycleC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, PocketCycleC2SPayload::hotbarSlot,
                    ByteBufCodecs.BOOL, PocketCycleC2SPayload::forward,
                    PocketCycleC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
