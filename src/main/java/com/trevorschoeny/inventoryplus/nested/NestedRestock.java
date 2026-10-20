package com.trevorschoeny.inventoryplus.nested;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevlar.slotkit.api.item.ItemScope;
import com.trevlar.slotkit.api.item.ItemSources;

import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.RESTOCK_PUT;
import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.RESTOCK_TAKE;

/**
 * Restock from inside carried containers: the server's half (SlotKit's item sources,
 * client chooses and server acts).
 *
 * <p>Two requests, both answered on the server thread:
 * <ul>
 *   <li>{@link Take}: the client found the item at {@code path} (a shulker box or bundle
 *       in the inventory, any depth) with {@code ItemSources.find} and asks for it in
 *       {@code target}. The client chose, because only it knows the player's locks.</li>
 *   <li>{@link FromEnderChest}: the client can't see the ender chest's contents unless
 *       it's open, so here the server chooses: the nearest item of the probe's kind in the
 *       ender chest (and, when asked, shulkers and bundles inside it).</li>
 * </ul>
 *
 * <p>The server trusts neither request further than this:
 * <ul>
 *   <li>The target is only ever the requesting player's own hotbar slot or offhand, and
 *       the take is capped at what fits there. A client can't ask for more than one stack's
 *       worth, or for a slot it shouldn't touch.</li>
 *   <li>A {@link Take} acts only if the item at the path is still the same item with the
 *       same components as the copy the client saw ({@code isSameItemSameComponents}).</li>
 *   <li>The target must allow Restock's put, and a {@link Take}'s top-level slot Restock's
 *       take, as far as the server knows (a lock kept only on the client is the client's
 *       to honour, and it does, before sending).</li>
 * </ul>
 * Anything else drops the request; the item stays where it was.
 *
 * <p>Whether a server answers at all is how the client knows the server runs Inventory
 * Plus: Fabric's {@code ClientPlayNetworking.canSend} is true only for a channel the
 * server registered a receiver for.
 */
public final class NestedRestock {

    private NestedRestock() {}

    /** The offhand's index in the player's {@link Inventory}. */
    public static final int OFFHAND = Inventory.SLOT_OFFHAND;

    /** Take the item at {@code path}, expected to be {@code expected}'s kind, into {@code target}. */
    public record Take(ItemPath path, ItemStack expected, int target) implements CustomPacketPayload {
        public static final Type<Take> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "restock_take_nested"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Take> CODEC = StreamCodec.composite(
                ItemPath.STREAM_CODEC, Take::path,
                ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, Take::expected,
                ByteBufCodecs.VAR_INT, Take::target,
                Take::new);
        @Override public Type<Take> type() { return TYPE; }
    }

    /**
     * Fill {@code target} with the nearest item of {@code probe}'s kind from the ender
     * chest, looking inside shulkers and bundles there when the flags say so.
     * {@code lockedSlots} has bit {@code i} set for each ender chest slot the player
     * locked (a client-side lock the server can't see): those slots, and any container in
     * them, are passed over.
     */
    public record FromEnderChest(ItemStack probe, int target, boolean shulkers, boolean bundles, int lockedSlots)
            implements CustomPacketPayload {
        public static final Type<FromEnderChest> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "restock_from_ender_chest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FromEnderChest> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, FromEnderChest::probe,
                ByteBufCodecs.VAR_INT, FromEnderChest::target,
                ByteBufCodecs.BOOL, FromEnderChest::shulkers,
                ByteBufCodecs.BOOL, FromEnderChest::bundles,
                ByteBufCodecs.VAR_INT, FromEnderChest::lockedSlots,
                FromEnderChest::new);
        @Override public Type<FromEnderChest> type() { return TYPE; }
    }

    /** Payload types on both sides, receivers on the server. Call once from the common initializer. */
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Take.TYPE, Take.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FromEnderChest.TYPE, FromEnderChest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Take.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server != null) server.execute(() -> take(player, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(FromEnderChest.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server != null) server.execute(() -> fromEnderChest(player, payload));
        });
    }

    static void take(ServerPlayer player, Take request) {
        ItemPath path = request.path();
        // Only containers in the inventory; the ender chest goes through FromEnderChest.
        if (path.source() != ItemRef.Source.INVENTORY || path.depth() == 0) return;
        if (!IPSlotOperations.allowsPlayerSlot(player, path.slot(), RESTOCK_TAKE)) return;
        int room = room(player, request.target(), request.expected());
        if (room <= 0) return;
        ItemRef ref = ItemSources.at(player, path);
        if (ref == null || !ItemStack.isSameItemSameComponents(ref.stack(), request.expected())) return;
        insert(player, request.target(), ref.take(room));
    }

    static void fromEnderChest(ServerPlayer player, FromEnderChest request) {
        ItemStack probe = request.probe();
        if (probe.isEmpty()) return;
        int room = room(player, request.target(), probe);
        if (room <= 0) return;
        ItemScope scope = ItemScope.inventory().enderChest(true)
                .shulkers(request.shulkers()).bundles(request.bundles());
        ItemStack[] taken = {ItemStack.EMPTY};
        ItemSources.walk(player, scope, ref -> {
            // The walk visits the inventory first; this request is for the ender chest only.
            if (ref.source() != ItemRef.Source.ENDER_CHEST || !ref.stack().is(probe.getItem())) return true;
            int slot = ref.path().slot();
            if (slot < 32 && (request.lockedSlots() & (1 << slot)) != 0) return true;
            // Whatever lands must stack with what's in the target already.
            ItemStack inTarget = player.getInventory().getItem(request.target());
            if (!inTarget.isEmpty() && !ItemStack.isSameItemSameComponents(inTarget, ref.stack())) return true;
            taken[0] = ref.take(Math.min(room, ref.stack().getMaxStackSize()));
            return taken[0].isEmpty();
        });
        insert(player, request.target(), taken[0]);
    }

    /**
     * How many of {@code kind} fit in {@code target}: a full stack when it's empty, the
     * rest of the stack when it holds the same item, else none. Also none for a target
     * that isn't a hotbar slot or the offhand, or that refuses Restock's put.
     */
    private static int room(ServerPlayer player, int target, ItemStack kind) {
        if (!(target >= 0 && target < Inventory.getSelectionSize()) && target != OFFHAND) return 0;
        if (kind.isEmpty() || !IPSlotOperations.allowsPlayerSlot(player, target, RESTOCK_PUT)) return 0;
        ItemStack there = player.getInventory().getItem(target);
        if (there.isEmpty()) return kind.getMaxStackSize();
        if (!ItemStack.isSameItemSameComponents(there, kind)) return 0;
        return there.getMaxStackSize() - there.getCount();
    }

    /** Puts {@code taken} into {@code target}, which {@link #room} said has space for it. */
    private static void insert(ServerPlayer player, int target, ItemStack taken) {
        if (taken.isEmpty()) return;
        Inventory inv = player.getInventory();
        ItemStack there = inv.getItem(target);
        if (there.isEmpty()) inv.setItem(target, taken);
        else there.grow(taken.getCount());
        inv.setChanged();
    }
}
