package com.trevorschoeny.inventoryplus.nested;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevlar.slotkit.api.item.ItemScope;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;
import com.trevlar.slotkit.api.window.TriBool;

import com.trevorschoeny.inventoryplus.api.InventoryPlusOperations;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Putting incoming items into carried containers: the server's half. "Container
 * first": an item that comes into the inventory goes into a carried shulker box, bundle
 * or the ender chest that already holds that item, and the inventory takes only what
 * doesn't fit.
 *
 * <p>Three moves do it, each with its own Reach places (Shulker Boxes, Bundles, Your
 * Ender Chest), all off by default:
 * <ul>
 *   <li><b>Item pickup</b> ({@code menukit:world_pickup}): every item given to the
 *       inventory, hooked at {@code Inventory.add} (pickups, {@code /give}, crafting
 *       returns).</li>
 *   <li><b>Shift-click in</b>: a shift-click on a storage slot (a chest, a barrel, a
 *       shulker box), hooked at the menu's click on the server.</li>
 *   <li><b>Move Matching in</b>: the client names the source slots ({@link MoveIn}); the
 *       rest of the move then runs as before.</li>
 * </ul>
 *
 * <p>The server decides where pickups land, so the client tells it the player's choices
 * ({@link Prefs}): which places each move may use, and which inventory slots hold a
 * container the move may put into. The second is the client's own Reach and locks (a
 * Slot lock on the slot holding a shulker keeps it closed), which the server can't see.
 * A player who never sent {@link Prefs} (no Inventory Plus on the client) gets vanilla.
 */
public final class CarriedPut {

    private CarriedPut() {}

    /** The three moves that put into carried containers, in the order {@link Prefs} packs them. */
    public enum Move {
        PICKUP(BehaviorKeys.INVENTORY_INSERT),
        SHIFT_CLICK(BehaviorKeys.SHIFT_CLICK_IN),
        MOVE_MATCHING(InventoryPlusOperations.MOVE_MATCHING_IN);

        /** The operation whose Reach (and locks) decides which slots' containers this move fills. */
        public final BehaviorKey<TriBool> operation;

        Move(BehaviorKey<TriBool> operation) {
            this.operation = operation;
        }
    }

    /** Bit offsets within one move's three bits of {@link Prefs#places}. */
    public static final int SHULKERS = 0, BUNDLES = 1, ENDER_CHEST = 2;

    /**
     * The player's choices. {@code places} has three bits per {@link Move} (bit
     * {@code move * 3 + place}); {@code slots} has, per move, bit {@code i} set for each
     * inventory slot (0 to 40) whose container the move may put into;
     * {@code enderLocked} has bit {@code i} set for each ender chest slot the player
     * locked.
     */
    public record Prefs(int places, List<Long> slots, int enderLocked) implements CustomPacketPayload {
        public static final Type<Prefs> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "carried_put_prefs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Prefs> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Prefs::places,
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(Move.values().length)), Prefs::slots,
                ByteBufCodecs.VAR_INT, Prefs::enderLocked,
                Prefs::new);
        @Override public Type<Prefs> type() { return TYPE; }

        public boolean place(Move move, int place) {
            return (places >> (move.ordinal() * 3 + place) & 1) != 0;
        }

        public boolean any(Move move) {
            return (places >> (move.ordinal() * 3) & 0b111) != 0;
        }

        public boolean slot(Move move, int slot) {
            return slot >= 0 && slot < 64 && move.ordinal() < slots.size()
                    && (slots.get(move.ordinal()) >> slot & 1) != 0;
        }
    }

    /** One Move Matching source: up to {@code count} from menu slot {@code menuSlot}. */
    public record Source(int menuSlot, int count) {
        static final StreamCodec<RegistryFriendlyByteBuf, Source> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Source::menuSlot, ByteBufCodecs.VAR_INT, Source::count, Source::new);
    }

    /** Move Matching in's first pass: put these sources' items into carried containers, then answer {@link Done}. */
    public record MoveIn(int containerId, List<Source> sources, int seq) implements CustomPacketPayload {
        public static final Type<MoveIn> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "carried_put_move_in"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MoveIn> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MoveIn::containerId,
                Source.CODEC.apply(ByteBufCodecs.list(256)), MoveIn::sources,
                ByteBufCodecs.VAR_INT, MoveIn::seq,
                MoveIn::new);
        @Override public Type<MoveIn> type() { return TYPE; }
    }

    /** The server's answer to {@link MoveIn}, sent after the menu's changes, so the client reads them first. */
    public record Done(int seq) implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "carried_put_done"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Done> CODEC =
                ByteBufCodecs.VAR_INT.map(Done::new, Done::seq).cast();
        @Override public Type<Done> type() { return TYPE; }
    }

    private static final Map<UUID, Prefs> PREFS = new ConcurrentHashMap<>();

    /** Payload types on both sides, receivers on the server. Call once from the common initializer. */
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Prefs.TYPE, Prefs.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MoveIn.TYPE, MoveIn.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Done.TYPE, Done.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Prefs.TYPE,
                (payload, context) -> PREFS.put(context.player().getUUID(), payload));
        ServerPlayNetworking.registerGlobalReceiver(MoveIn.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server != null) server.execute(() -> moveIn(player, payload));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PREFS.remove(handler.getPlayer().getUUID()));
    }

    /** For the self-checks: set a player's choices as if their client had sent them. */
    public static void setPrefs(UUID player, Prefs prefs) {
        PREFS.put(player, prefs);
    }

    /**
     * Puts as much of {@code stack} as fits into the player's carried containers that
     * already hold it, as {@code move} may, shrinking {@code stack} by what went in.
     * Nearest first: containers in the inventory (hotbar first, then deeper), then the
     * ender chest. Returns how many went in.
     */
    public static int into(ServerPlayer player, ItemStack stack, Move move) {
        if (stack.isEmpty()) return 0;
        Prefs prefs = PREFS.get(player.getUUID());
        if (prefs == null || !prefs.any(move)) return 0;
        boolean shulkers = prefs.place(move, SHULKERS), bundles = prefs.place(move, BUNDLES);
        boolean ender = prefs.place(move, ENDER_CHEST);
        ItemScope scope = ItemScope.inventory().shulkers(shulkers).bundles(bundles).enderChest(ender);

        // The containers that hold the item, by path, in walk order; the ender chest
        // itself when one of its own slots does.
        Set<ItemPath> containers = new LinkedHashSet<>();
        boolean[] enderHolds = {false};
        ItemSources.walk(player, scope, ref -> {
            if (!ItemStack.isSameItemSameComponents(ref.stack(), stack)) return true;
            ItemPath path = ref.path();
            if (ref.source() == ItemRef.Source.ENDER_CHEST) {
                if (enderLocked(prefs, path.slot())) return true;
                if (path.depth() == 0) enderHolds[0] = true;
                else containers.add(parent(path));
            } else if (ref.source() == ItemRef.Source.INVENTORY && path.depth() > 0) {
                if (prefs.slot(move, path.slot())) containers.add(parent(path));
            }
            return true;
        });

        int before = stack.getCount();
        for (ItemPath path : containers) {
            // A put makes the walk's other refs into that container stale: resolve afresh.
            ItemRef container = ItemSources.at(player, path);
            if (container == null) continue;
            ItemStack left = container.put(stack);
            stack.setCount(left.getCount());
            if (stack.isEmpty()) break;
        }
        if (!stack.isEmpty() && enderHolds[0]) intoEnderChest(player, stack, prefs);
        return before - stack.getCount();
    }

    /** The path of the container {@code path}'s item sits in. */
    private static ItemPath parent(ItemPath path) {
        return new ItemPath(path.source(), path.slot(), path.inside().subList(0, path.depth() - 1), path.attachment());
    }

    private static boolean enderLocked(Prefs prefs, int slot) {
        return slot < 32 && (prefs.enderLocked() >> slot & 1) != 0;
    }

    /** Merges into the ender chest's matching stacks, then its empty slots, skipping locked ones. */
    private static void intoEnderChest(ServerPlayer player, ItemStack stack, Prefs prefs) {
        Container ender = player.getEnderChestInventory();
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < ender.getContainerSize() && !stack.isEmpty(); i++) {
                if (enderLocked(prefs, i)) continue;
                ItemStack there = ender.getItem(i);
                int limit = Math.min(ender.getMaxStackSize(stack), stack.getMaxStackSize());
                if (pass == 0 && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < limit) {
                    int n = Math.min(limit - there.getCount(), stack.getCount());
                    there.grow(n);
                    stack.shrink(n);
                } else if (pass == 1 && there.isEmpty()) {
                    int n = Math.min(limit, stack.getCount());
                    ender.setItem(i, stack.split(n));
                }
            }
        }
        ender.setChanged();
    }

    /** The storage slots a shift-click or Move Matching may take from: never a result or fuel slot. */
    private static final Set<SlotGroupCategory> STORAGE = Set.of(
            SlotGroupCategory.CHEST_STORAGE, SlotGroupCategory.SHULKER_STORAGE, SlotGroupCategory.DISPENSER_STORAGE,
            SlotGroupCategory.HOPPER_STORAGE, SlotGroupCategory.MOUNT_STORAGE);

    /**
     * Takes up to {@code count} of {@code slot}'s items into carried containers, as
     * {@code move}, when {@code slot} is a storage slot that isn't the player's own and
     * lets {@code takeAs} take from it. Returns how many went in.
     */
    public static int fromSlot(ServerPlayer player, AbstractContainerMenu menu, Slot slot, int count,
                               Move move, BehaviorKey<TriBool> takeAs) {
        if (count <= 0 || !slot.hasItem() || slot.container == player.getInventory()) return 0;
        Prefs prefs = PREFS.get(player.getUUID());
        if (prefs == null || !prefs.any(move)) return 0;
        if (!STORAGE.contains(SlotRef.of(menu, slot, player).category())) return 0;
        if (!slot.mayPickup(player) || !SlotOperations.allows(menu, slot, player, takeAs)) return 0;
        ItemStack moving = slot.getItem().copyWithCount(Math.min(count, slot.getItem().getCount()));
        int moved = into(player, moving, move);
        if (moved > 0) {
            slot.remove(moved);
            slot.setChanged();
        }
        return moved;
    }

    private static void moveIn(ServerPlayer player, MoveIn request) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null && menu.containerId == request.containerId()) {
            for (Source s : request.sources()) {
                if (s.menuSlot() < 0 || s.menuSlot() >= menu.slots.size()) continue;
                fromSlot(player, menu, menu.slots.get(s.menuSlot()), s.count(), Move.MOVE_MATCHING,
                        InventoryPlusOperations.MOVE_MATCHING_OUT);
            }
            // Sync now, so the client reads the result before the answer.
            menu.broadcastChanges();
        }
        ServerPlayNetworking.send(player, new Done(request.seq()));
    }
}
