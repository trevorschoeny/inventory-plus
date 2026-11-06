package com.trevorschoeny.inventoryplus.rules;

import com.trevlar.menukit.api.element.ItemMatch;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.item.ItemContents;
import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.item.LiveItem;
import com.trevlar.slotkit.api.state.SlotState;
import com.trevlar.slotkit.api.state.SlotStateChannel;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Reserved slots: a slot that holds out for one item and takes nothing else, by any
 * hand: clicks, shift-clicks, drags, number keys, pickups, hoppers. A reserved slot
 * that runs out keeps its icon.
 *
 * <p>A reservation is a MenuKit {@link ItemMatch}, as the reserve popup's item picker
 * sets it: the item always counts, each field the player filled in (name, enchantments,
 * a level or "any level", other components) narrows it, and a blank one isn't checked.
 * Its {@link ItemMatch#count() count} is the slot's cap, {@link ItemMatch#NONE} for the
 * item's own stack size. {@link #NONE} (an empty rule) is no reservation.
 *
 * <p>Built on SlotKit's general rules, as locks are: the value is SHARED slot state, so
 * it lives on a placed container's block entity, travels on a shulker box's item, and
 * stays on the player for their inventory; the refusal is a put veto and the cap a
 * limit, both for both modes, so every put seam and automation honors them.
 *
 * <p>The server stores and enforces reservations, so setting one needs Inventory Plus
 * on the server. A client asks with {@link Reserve}.
 */
public final class Reservations {

    private Reservations() {}

    /** No reservation: the slot takes anything. */
    public static final ItemMatch NONE = new ItemMatch(ItemStack.EMPTY);

    /** Untrusted on decode: a client names the item. */
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemMatch> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(ItemMatch.CODEC);

    // Written only through reserve(), which moves out what no longer belongs, and
    // set() for a box item; a client asks with Reserve, never with a raw write.
    private static final SlotStateChannel<ItemMatch> CHANNEL = SlotState.register(ModeScope.BOTH,
            Identifier.fromNamespaceAndPath("inventoryplus", "reservation"), ItemMatch.CODEC, STREAM_CODEC, NONE,
            SlotStateChannel.Visibility.SHARED, SlotStateChannel.CanWrite.DENY);

    // Reading

    /** Whether {@code rule} is no reservation. */
    public static boolean isNone(ItemMatch rule) {
        return rule.stack().isEmpty();
    }

    /** The reservation on a slot, wherever it lives; {@link #NONE} for none. Either side. */
    public static ItemMatch get(SlotRef ref) {
        return CHANNEL.get(ref);
    }

    /** The reservation on slot {@code index} of the container {@code box} carries (a shulker box item). */
    public static ItemMatch get(ItemStack box, int index) {
        return CHANNEL.get(box, index);
    }

    /** Whether {@code stack} may go where {@code rule} reserves: always, with no reservation. */
    public static boolean allows(ItemMatch rule, ItemStack stack) {
        return isNone(rule) || rule.matches(stack);
    }

    /** How many of {@code stack} a slot reserved by {@code rule} holds: the cap, never more than the stack size. */
    public static int limit(ItemMatch rule, ItemStack stack) {
        int max = stack.getMaxStackSize();
        return rule.count() == ItemMatch.NONE ? max : Math.min(rule.count(), max);
    }

    // Writing, server side

    /**
     * Reserves {@code slot} of {@code player}'s open menu (or clears it with {@link #NONE}),
     * as the player's own action. Whatever no longer belongs (a different item, or more
     * than the cap) goes into the player's inventory, or is dropped when it doesn't fit.
     */
    public static void reserve(ServerPlayer player, Slot slot, ItemMatch rule) {
        int index = slot.getContainerSlot();
        if (slot.container instanceof ItemContents contents) {
            // A box opened from the inventory: the reservation goes on the box item.
            ItemPath path = contents.itemPath(player);
            LiveItem live = path == null ? null : ItemSources.live(player, path);
            if (live == null) return;
            CHANNEL.set(live.stack(), index, rule);
            live.commit();
        } else {
            CHANNEL.set(player, slot, rule);
        }
        ItemStack there = slot.getItem();
        if (isNone(rule) || there.isEmpty()) return;
        int keep = rule.matches(there) ? Math.min(there.getCount(), limit(rule, there)) : 0;
        if (keep == there.getCount()) return;
        ItemStack out = slot.remove(there.getCount() - keep);
        // Into the inventory, the rest dropped; the slot just reserved refuses it, so it doesn't come back.
        player.getInventory().placeItemBackInInventory(out, Prediction.SERVER_ONLY);
    }

    /** Sets slot {@code index} of a placed container from server code, with no player. Moves nothing out. */
    public static void set(Container container, int index, ItemMatch rule) {
        CHANNEL.set(new Slot(container, index, 0, 0), rule);
    }

    /** Sets slot {@code index} of the container {@code box} carries. The caller puts the item back where it lives. */
    public static void set(ItemStack box, int index, ItemMatch rule) {
        CHANNEL.set(box, index, rule);
    }

    // The client's request

    /** Reserve slot {@code menuSlot} of the menu the client has open, as a click names a slot. */
    public record Reserve(int containerId, int menuSlot, ItemMatch rule) implements CustomPacketPayload {
        public static final Type<Reserve> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "reserve"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reserve> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reserve::containerId, ByteBufCodecs.VAR_INT, Reserve::menuSlot,
                STREAM_CODEC, Reserve::rule, Reserve::new);

        @Override
        public Type<Reserve> type() {
            return TYPE;
        }
    }

    /** The veto, the limit and the request. Once, from the common initializer. */
    public static void register() {
        SlotOperations.vetoPut(ModeScope.BOTH, Reservations::refuses);
        SlotOperations.limit(ModeScope.BOTH, Reservations::cap);
        PayloadTypeRegistry.serverboundPlay().register(Reserve.TYPE, Reserve.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Reserve.TYPE,
                (payload, context) -> context.server().execute(() -> handle(context.player(), payload)));
    }

    /** A put the reservation refuses: another item. Its cap is {@link #cap}. */
    private static boolean refuses(SlotRef ref, @Nullable SlotGroupId group, BehaviorKey<?> operation, ItemStack incoming) {
        return !allows(get(ref), incoming);
    }

    /** A matching reservation's cap, as SlotKit's limit rule. */
    private static int cap(SlotRef ref, @Nullable SlotGroupId group, ItemStack incoming) {
        ItemMatch rule = get(ref);
        return isNone(rule) || !rule.matches(incoming) ? SlotOperations.NO_LIMIT : limit(rule, incoming);
    }

    // A client's request: a slot of the menu it has open, or of its own inventory menu.
    private static void handle(ServerPlayer player, Reserve request) {
        if (player.isSpectator()) return;
        AbstractContainerMenu menu = player.containerMenu.containerId == request.containerId() ? player.containerMenu
                : player.inventoryMenu.containerId == request.containerId() ? player.inventoryMenu : null;
        if (menu == null || request.menuSlot() < 0 || request.menuSlot() >= menu.slots.size()) return;
        reserve(player, menu.slots.get(request.menuSlot()), request.rule());
    }
}
