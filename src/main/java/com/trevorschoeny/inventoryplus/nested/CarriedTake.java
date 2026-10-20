package com.trevorschoeny.inventoryplus.nested;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevlar.slotkit.api.item.ItemScope;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.TriBool;

import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import io.netty.buffer.ByteBuf;

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

import java.util.function.Predicate;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.RESTOCK_PUT;
import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.RESTOCK_TAKE;

/**
 * Taking an item out of a carried container: the server's half (SlotKit's item sources,
 * client chooses and server acts). Restock and pick block use it.
 *
 * <p>Two requests, both answered on the server thread:
 * <ul>
 *   <li>{@link Take}: the client found the item at {@code path} (a shulker box or bundle
 *       in the inventory, any depth) with {@code ItemSources.find} and asks for it in
 *       {@code target}. The client chose, because only it knows the player's locks.</li>
 *   <li>{@link FromEnderChest}: the client can't see the ender chest's contents unless
 *       it's open, so here the server chooses: the nearest matching item in the ender
 *       chest (and, when asked, shulkers and bundles inside it).</li>
 * </ul>
 *
 * <p>Each request names its {@link Purpose}, which fixes where the item may land and
 * which operations are asked. The server trusts neither request further than this:
 * <ul>
 *   <li>The target is only ever one of the requesting player's own slots that the purpose
 *       allows, and the take is capped at what fits there.</li>
 *   <li>A {@link Take} acts only if the item at the path is still the same item with the
 *       same components as the copy the client saw ({@code isSameItemSameComponents}).</li>
 *   <li>The target must allow the purpose's put, and the top-level slot its take, as far
 *       as the server knows (a lock kept only on the client is the client's to honour,
 *       and it does, before sending).</li>
 * </ul>
 * Anything else drops the request; the item stays where it was.
 *
 * <p>Whether a server answers at all is how the client knows the server runs Inventory
 * Plus: Fabric's {@code ClientPlayNetworking.canSend} is true only for a channel the
 * server registered a receiver for.
 */
public final class CarriedTake {

    private CarriedTake() {}

    /** The offhand's index in the player's {@link Inventory}. */
    public static final int OFFHAND = Inventory.SLOT_OFFHAND;

    /** What a take is for: where the item may land, and which operations judge it. */
    public enum Purpose {
        /** Restock: fill a hotbar slot or the offhand that ran out, up to what fits. */
        RESTOCK(RESTOCK_TAKE),
        /**
         * Pick block: a whole stack into an empty inventory slot, ahead of vanilla's own
         * pick, which then finds it there and brings it to the hand. Judged as the hotbar
         * swap vanilla's pick is, so a slot lock that stops swaps stops it.
         */
        PICK(BehaviorKeys.HOTBAR_SWAP);

        /** The operation the item's top-level slot must allow. */
        final BehaviorKey<TriBool> take;

        Purpose(BehaviorKey<TriBool> take) {
            this.take = take;
        }

        /** The operation the item's top-level slot must allow; the client asks it too, with its own locks. */
        public BehaviorKey<TriBool> take() {
            return take;
        }

        static final StreamCodec<ByteBuf, Purpose> CODEC = ByteBufCodecs.idMapper(i -> values()[i], Purpose::ordinal);
    }

    /** Take the item at {@code path}, expected to be {@code expected}'s kind, into {@code target}. */
    public record Take(ItemPath path, ItemStack expected, int target, Purpose purpose) implements CustomPacketPayload {
        public static final Type<Take> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "carried_take"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Take> CODEC = StreamCodec.composite(
                ItemPath.STREAM_CODEC, Take::path,
                ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, Take::expected,
                ByteBufCodecs.VAR_INT, Take::target,
                Purpose.CODEC, Take::purpose,
                Take::new);
        @Override public Type<Take> type() { return TYPE; }
    }

    /**
     * Fill {@code target} with the nearest match for {@code probe} from the ender chest,
     * looking inside shulkers and bundles there when the flags say so. {@code lockedSlots}
     * has bit {@code i} set for each ender chest slot the player locked (a client-side
     * lock the server can't see): those slots, and any container in them, are passed over.
     */
    public record FromEnderChest(ItemStack probe, int target, Purpose purpose, boolean shulkers, boolean bundles,
                                 int lockedSlots) implements CustomPacketPayload {
        public static final Type<FromEnderChest> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "carried_from_ender_chest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FromEnderChest> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, FromEnderChest::probe,
                ByteBufCodecs.VAR_INT, FromEnderChest::target,
                Purpose.CODEC, FromEnderChest::purpose,
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
        if (!IPSlotOperations.allowsPlayerSlot(player, path.slot(), request.purpose().take)) return;
        int room = room(player, request.purpose(), request.target(), request.expected());
        if (room <= 0) return;
        ItemRef ref = ItemSources.at(player, path);
        if (ref == null || !ItemStack.isSameItemSameComponents(ref.stack(), request.expected())) return;
        insert(player, request.target(), ref.take(room));
    }

    static void fromEnderChest(ServerPlayer player, FromEnderChest request) {
        ItemStack probe = request.probe();
        if (probe.isEmpty()) return;
        Purpose purpose = request.purpose();
        int room = room(player, purpose, request.target(), probe);
        if (room <= 0) return;
        Predicate<ItemStack> wanted = matcher(purpose, probe);
        ItemScope scope = ItemScope.inventory().enderChest(true)
                .shulkers(request.shulkers()).bundles(request.bundles());
        ItemStack[] taken = {ItemStack.EMPTY};
        ItemSources.walk(player, scope, ref -> {
            // The walk visits the inventory first; this request is for the ender chest only.
            if (ref.source() != ItemRef.Source.ENDER_CHEST || !wanted.test(ref.stack())) return true;
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
     * What an ender chest item must be for {@code purpose}: restock refills the same item
     * whatever its components (as Restock's inventory search does); pick wants exactly
     * the item vanilla's pick asks for, components and all, as vanilla's own inventory
     * search does.
     */
    private static Predicate<ItemStack> matcher(Purpose purpose, ItemStack probe) {
        return switch (purpose) {
            case RESTOCK -> s -> s.is(probe.getItem());
            case PICK -> s -> ItemStack.isSameItemSameComponents(s, probe);
        };
    }

    /**
     * How many of {@code kind} {@code purpose} may put in {@code target}; none for a
     * target the purpose doesn't allow, or one that refuses its put.
     * <ul>
     *   <li>{@link Purpose#RESTOCK}: a hotbar slot or the offhand. A full stack when it's
     *       empty, the rest of the stack when it holds the same item, else none.</li>
     *   <li>{@link Purpose#PICK}: an empty hotbar or main inventory slot, a full stack.</li>
     * </ul>
     */
    private static int room(ServerPlayer player, Purpose purpose, int target, ItemStack kind) {
        if (kind.isEmpty()) return 0;
        Inventory inv = player.getInventory();
        return switch (purpose) {
            case RESTOCK -> {
                if (!(Inventory.isHotbarSlot(target) || target == OFFHAND)) yield 0;
                if (!IPSlotOperations.allowsPlayerSlot(player, target, RESTOCK_PUT)) yield 0;
                ItemStack there = inv.getItem(target);
                if (there.isEmpty()) yield kind.getMaxStackSize();
                if (!ItemStack.isSameItemSameComponents(there, kind)) yield 0;
                yield there.getMaxStackSize() - there.getCount();
            }
            case PICK -> emptyInventorySlot(player, target) ? kind.getMaxStackSize() : 0;
        };
    }

    /**
     * {@code target} is an empty hotbar or main inventory slot that takes an item put
     * into the inventory (a slot lock that stops pickups stops this too).
     */
    private static boolean emptyInventorySlot(ServerPlayer player, int target) {
        return target >= 0 && target < Inventory.INVENTORY_SIZE
                && player.getInventory().getItem(target).isEmpty()
                && IPSlotOperations.allowsPlayerSlot(player, target, BehaviorKeys.INVENTORY_INSERT);
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
