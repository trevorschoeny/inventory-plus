package com.trevorschoeny.inventoryplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> Server: "bulk-move all matching items from a region."
 *
 * <p>Sent when the player Shift+double-clicks a slot. The server iterates
 * every slot in the named region and shift-clicks (quickMoveStack) each one
 * that contains the matching item type. This effectively moves ALL of that
 * item from the source region to wherever shift-click would normally send it.
 *
 * <p>Two fields:
 * <ul>
 *   <li>{@code regionName} — the region the player clicked in (source)</li>
 *   <li>{@code itemId} — the item's resource location (e.g., "minecraft:cobblestone")
 *       so the server knows which item type to match</li>
 * </ul>
 */
public record BulkMoveC2SPayload(String regionName, String itemId)
        implements CustomPacketPayload {

    public static final Type<BulkMoveC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("inventory-plus", "bulk_move_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BulkMoveC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, BulkMoveC2SPayload::regionName,
                    ByteBufCodecs.STRING_UTF8, BulkMoveC2SPayload::itemId,
                    BulkMoveC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
