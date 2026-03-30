package com.trevorschoeny.inventoryplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> Server: "move all items matching the destination into it from the source."
 *
 * <p>Sent when the player clicks the "Move Matching" button near a container.
 * The server scans the destination group for unique item types, then iterates
 * the source group and transfers every matching slot into the destination.
 *
 * <p>Four fields:
 * <ul>
 *   <li>{@code sourceGroupName} -- the source region group (e.g., "player_storage")</li>
 *   <li>{@code destGroupName} -- the destination region group (e.g., "container_storage")</li>
 *   <li>{@code destRegionName} -- the specific destination region (e.g., "peek_shulker").
 *       When non-empty, items are transferred directly into this region's slots
 *       instead of using vanilla's quickMoveStack routing. This ensures items go
 *       to the intended container when multiple SIMPLE regions share a group.</li>
 *   <li>{@code includeHotbar} -- whether to include hotbar slots in the source</li>
 * </ul>
 */
public record MoveMatchingC2SPayload(String sourceGroupName, String destGroupName,
                                      String destRegionName, boolean includeHotbar)
        implements CustomPacketPayload {

    public static final Type<MoveMatchingC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("inventory-plus", "move_matching_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveMatchingC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MoveMatchingC2SPayload::sourceGroupName,
                    ByteBufCodecs.STRING_UTF8, MoveMatchingC2SPayload::destGroupName,
                    ByteBufCodecs.STRING_UTF8, MoveMatchingC2SPayload::destRegionName,
                    ByteBufCodecs.BOOL, MoveMatchingC2SPayload::includeHotbar,
                    MoveMatchingC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
