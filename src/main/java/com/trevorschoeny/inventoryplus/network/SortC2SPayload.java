package com.trevorschoeny.inventoryplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> Server: "sort the region I'm hovering."
 *
 * <p>Carries the region name so the server can look it up in the
 * {@link com.trevorschoeny.menukit.MKRegionRegistry} for the player's
 * open menu. The server validates the region exists and calls
 * {@link com.trevorschoeny.menukit.MKContainerSort#sortRegion} —
 * vanilla's broadcastChanges syncs the result automatically.
 */
public record SortC2SPayload(String regionName) implements CustomPacketPayload {

    public static final Type<SortC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("inventory-plus", "sort_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SortC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SortC2SPayload::regionName,
                    SortC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
