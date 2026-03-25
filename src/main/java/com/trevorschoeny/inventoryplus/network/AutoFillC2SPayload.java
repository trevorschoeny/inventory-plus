package com.trevorschoeny.inventoryplus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> Server: "autofill my inventory from shulker boxes."
 *
 * <p>A pure trigger packet with no payload data. The client sends this
 * when the player presses the autofill keybind. The server scans the
 * player's inventory for partial stacks and fills them from shulker
 * boxes carried in the same inventory.
 *
 * <p>No data is needed because all the information (which stacks are
 * partial, which shulkers exist) lives in the server's authoritative
 * inventory state.
 */
public record AutoFillC2SPayload() implements CustomPacketPayload {

    public static final Type<AutoFillC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("inventory-plus", "autofill_c2s"));

    // Empty payload — StreamCodec that reads/writes nothing
    public static final StreamCodec<RegistryFriendlyByteBuf, AutoFillC2SPayload> STREAM_CODEC =
            StreamCodec.unit(new AutoFillC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
