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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Putting incoming items into carried containers: the server's half. "Container
 * first": an item that comes into the inventory goes into a carried shulker box, bundle
 * or the ender chest, and the inventory takes only what doesn't fit.
 *
 * <p>Which containers is Reach's rule for a put. Each row (the container's place, and
 * the inventory slot it sits in) lets the item in if its Empty slots box is ticked, or
 * its Matching box is ticked and the container already holds the item
 * ({@code ItemStack.isSameItem}). Containers that already hold it fill first.
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

    /** The places, in the order {@link Prefs} packs them. */
    public static final int SHULKERS = 0, BUNDLES = 1, ENDER_CHEST = 2;

    /** The two lists, in the order {@link Prefs} packs them. */
    public static final int EMPTY_SLOTS = 0, MATCHING = 1;

    /**
     * The player's choices. {@code places} has a bit per {@link Move}, place and list
     * (bit {@code move * 6 + place * 2 + list}), set where that box is ticked.
     * {@code slots} has two masks per move ({@code move * 2 + list}), bit {@code i} set
     * for each inventory slot (0 to 40) whose rows let a put in: every row by Empty
     * slots, or every row by one box or the other. {@code enderLocked} has bit {@code i}
     * set for each ender chest slot the player locked.
     */
    public record Prefs(int places, List<Long> slots, int enderLocked) implements CustomPacketPayload {
        public static final Type<Prefs> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "carried_put_prefs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Prefs> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Prefs::places,
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(Move.values().length * 2)), Prefs::slots,
                ByteBufCodecs.VAR_INT, Prefs::enderLocked,
                Prefs::new);
        @Override public Type<Prefs> type() { return TYPE; }

        /** Bit {@code list} of {@code place} for {@code move}. */
        public static int bit(Move move, int place, int list) {
            return 1 << (move.ordinal() * 6 + place * 2 + list);
        }

        /** Whether the place's row lets an item in: Empty slots, or Matching where the container {@code holds} it. */
        public boolean place(Move move, int place, boolean holds) {
            return (places & bit(move, place, EMPTY_SLOTS)) != 0 || holds && (places & bit(move, place, MATCHING)) != 0;
        }

        /** Whether the move may use the place at all, so it is worth looking inside. */
        public boolean opens(Move move, int place) {
            return place(move, place, true);
        }

        public boolean any(Move move) {
            return (places >> (move.ordinal() * 6) & 0b111111) != 0;
        }

        /** Whether the rows of inventory slot {@code slot} let the move put into the container there. */
        public boolean slot(Move move, int slot, boolean holds) {
            int mask = move.ordinal() * 2 + (holds ? MATCHING : EMPTY_SLOTS);
            return slot >= 0 && slot < 64 && mask < slots.size() && (slots.get(mask) >> slot & 1) != 0;
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
     * Puts as much of {@code stack} as fits into the player's carried containers, as
     * {@code move}'s rows allow, shrinking {@code stack} by what went in. Containers
     * that already hold the item first, then those that may take it new; each nearest
     * first (in the inventory, hotbar first, then deeper; then the ender chest's own).
     * The ender chest itself comes last. Returns how many went in.
     */
    public static int into(ServerPlayer player, ItemStack stack, Move move) {
        if (stack.isEmpty()) return 0;
        Prefs prefs = PREFS.get(player.getUUID());
        if (prefs == null || !prefs.any(move)) return 0;
        ItemScope scope = ItemScope.inventory().shulkers(prefs.opens(move, SHULKERS))
                .bundles(prefs.opens(move, BUNDLES)).enderChest(prefs.opens(move, ENDER_CHEST));

        // Every carried container by path, in walk order, with its place; which of them
        // already hold the item; and whether the ender chest's own slots do.
        Map<ItemPath, Integer> containers = new LinkedHashMap<>();
        Set<ItemPath> holding = new HashSet<>();
        boolean[] enderHolds = {false};
        ItemSources.walk(player, scope, ref -> {
            ItemPath path = ref.path();
            if (ref.source() == ItemRef.Source.ENDER_CHEST && enderLocked(prefs, path.slot())) return true;
            int place = placeOf(ref.stack());
            if (place >= 0) containers.put(path, place);
            if (ItemStack.isSameItem(ref.stack(), stack)) {
                if (path.depth() > 0) holding.add(parent(path));
                else if (ref.source() == ItemRef.Source.ENDER_CHEST) enderHolds[0] = true;
            }
            return true;
        });

        List<ItemPath> order = new ArrayList<>(containers.size());
        for (ItemPath path : containers.keySet()) if (holding.contains(path)) order.add(path);
        for (ItemPath path : containers.keySet()) if (!holding.contains(path)) order.add(path);

        int before = stack.getCount();
        for (ItemPath path : order) {
            boolean holds = holding.contains(path);
            if (!lets(prefs, move, path, containers.get(path), holds)) continue;
            // A put makes the walk's other refs into that container stale: resolve afresh.
            ItemRef container = ItemSources.at(player, path);
            if (container == null) continue;
            ItemStack left = container.put(stack);
            stack.setCount(left.getCount());
            if (stack.isEmpty()) break;
        }
        if (!stack.isEmpty() && prefs.place(move, ENDER_CHEST, enderHolds[0])) intoEnderChest(player, stack, prefs);
        return before - stack.getCount();
    }

    /**
     * Whether every row on the container at {@code path} lets {@code move} put in: its
     * own place, and the inventory slot it sits in, or the ender chest it sits in.
     * ponytail: a container inside another is judged by its own place and the top slot
     * only, not the places of the containers around it; judge each level if nesting
     * ever matters.
     */
    private static boolean lets(Prefs prefs, Move move, ItemPath path, int place, boolean holds) {
        if (!prefs.place(move, place, holds)) return false;
        return switch (path.source()) {
            case INVENTORY -> prefs.slot(move, path.slot(), holds);
            case ENDER_CHEST -> prefs.place(move, ENDER_CHEST, holds);
            default -> false;
        };
    }

    /** The place a carried container counts as: a shulker box, a bundle, or -1 for anything else. */
    private static int placeOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) return SHULKERS;
        return stack.has(DataComponents.BUNDLE_CONTENTS) ? BUNDLES : -1;
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
