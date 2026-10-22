package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.api.WorldStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.EnderChestBlock;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State and persistence for Locked Slots.
 *
 * <h3>Scope</h3>
 *
 * Player slots: hotbar (container-slot 0-8), main inventory (9-35), armor
 * (36-39), offhand (40). Also the ender chest, SlotKit created
 * slots and placed containers, each in its own namespace. Ephemeral slots
 * (crafting input and result, anvil inputs and so on) are not lockable.
 *
 * <h3>Persistence (per world)</h3>
 *
 * File: {@code config/inventoryplus/locked-slots.json}. Every lock names its
 * lock group, so each namespace maps a slot to a group id. Version 2:
 *
 * <pre>{@code
 * {
 *   "version": 2,
 *   "perWorld": {
 *     "singleplayer:New World": { "9": "slot_lock", "36": "g2" }
 *   },
 *   "enderPerWorld": { ... }, "createdPerWorld": { ... },
 *   "containersPerWorld": { "<world>": { "<container key>": { "4": "slot_lock" } } }
 * }
 * }</pre>
 *
 * <p>Version 1 stored bare index arrays. They still load, each lock becoming
 * a Slot lock, and the file is rewritten as version 2 at once: that is the
 * 1.5.x migration.
 */
public final class LockedSlots {

    private LockedSlots() {}

    private static final int CURRENT_VERSION = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Inclusive upper bound on player container-slot indices that can be locked. */
    public static final int MAX_PLAYER_CONTAINER_SLOT = 40;

    /** Inclusive upper bound on the inventory and hotbar subset (the slots Column Cycler works on). */
    public static final int MAX_INV_HOTBAR_CONTAINER_SLOT = 35;

    /**
     * Ender chest slot count (container-slot 0-26). Ender locks live in their
     * own per-world namespace ({@link #ENDER}) so ender slot N does
     * not collide with player container-slot N (both are 0-based).
     */
    public static final int ENDER_SLOT_COUNT = 27;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("locked-slots.json");
    }

    // The four lock namespaces. Each is a WorldStore: one immutable value per
    // world, read without ever creating anything, written only through
    // modify/set, and saved only when a write actually changes something.
    // Before 1.6.0 these were four hand-rolled Map<worldId, ...> fields with
    // three different ideas of what "nothing stored yet" meant, and one of
    // them handed an immutable Map.of() to a caller that then removed from it:
    // the first block broken in a world with no container locks crashed the
    // game. See WorldStore for the contract that rules that out.
    //
    // Each store maps a locked slot to the id of the lock group on it. One group
    // per kind per target, so a slot holds one Slot-kind lock.

    /** Player-owned slot locks (inventory, hotbar, armor, offhand): container-slot index to group id. */
    private static final WorldStore<Map<Integer, String>> PLAYER = WorldStore.sortedMap(LockedSlots::save);

    /**
     * Ender chest slot locks, keyed by world. Separate from {@link #PLAYER}
     * because ender slot indices (0-26) overlap player container-slot indices.
     * Ender is single-viewer (your own ender, no other player can open it), so
     * it stays client-side per player just like player slots. Doing it on the
     * client also means it works on a vanilla server with nothing installed there.
     */
    private static final WorldStore<Map<Integer, String>> ENDER = WorldStore.sortedMap(LockedSlots::save);
    /**
     * worldId -> created-slot keys ({@link CreatedSlotKey}). SlotKit
     * slots (pockets, equipment) locked by the player. Own namespace, like ender,
     * because a created slot's container index collides with vanilla's.
     * Created slots are treated exactly like vanilla slots for every lock
     * feature.
     */
    private static final WorldStore<Map<String, String>> CREATED = WorldStore.sortedMap(LockedSlots::save);
    /**
     * worldId -> container identity ({@code block:<dim>:<x>,<y>,<z>}, see
     * {@link ContainerIdentity}) -> locked slot indices. Placed-container locks,
     * client-side and per player. This deliberately reverses the shared model
     * Inventory Max had: IP locks them for you alone. Claimed before any
     * downstream provider, so no shared channel answers for placed containers
     * while IP is present.
     */
    private static final WorldStore<Map<String, Map<Integer, String>>> CONTAINERS =
            WorldStore.of(Collections.emptySortedMap(), LockedSlots::freezeContainers, LockedSlots::save);

    /**
     * The immutable form of one world's container locks: container keys in
     * order, each with its locked slot indices in order. A container whose
     * last lock was removed is dropped here, so "no locks on this chest" is
     * never stored as an empty set, the same rule the store applies to worlds.
     */
    private static Map<String, Map<Integer, String>> freezeContainers(Map<String, Map<Integer, String>> byKey) {
        java.util.TreeMap<String, Map<Integer, String>> out = new java.util.TreeMap<>();
        byKey.forEach((key, slots) -> {
            if (!slots.isEmpty()) out.put(key, Collections.unmodifiableSortedMap(new java.util.TreeMap<>(slots)));
        });
        return Collections.unmodifiableSortedMap(out);
    }

    // Per-open-screen cache of the container key. isLockedSlot runs per slot per
    // frame; resolving the identity re-reads the tracker and a block state, so
    // it is done once per menu and reused until the menu changes.
    /**
     * The menu {@link #cachedContainerKey} is for, by identity: container ids
     * start again in each world, so a key cached by id outlived its menu and
     * was read for the next world's menu with the same id.
     */
    private static @Nullable AbstractContainerMenu cachedMenu;
    private static @Nullable String cachedContainerKey;

    private static boolean loaded = false;

    /**
     * Reads the file. {@code pairedSlotsIn} names, per world, the player slots
     * the Column Cycler's old pairing locked (see the migration note in the
     * body); it is consulted only when upgrading a version 1 file.
     */
    public static void load(java.util.function.Function<String, Set<Integer>> pairedSlotsIn) {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            boolean[] v1 = {false};
            Set<String> worlds = new HashSet<>();
            // Player slots, with the 1.5.x pairing's writes dropped. In 1.5.x,
            // "Lock the slots the cyclers use" wrote a real Slot
            // lock onto each cycle slot and its column's hotbar slot, and
            // removed it again when the cycle went (restoring any lock the
            // player had there, but only within one screen session). The
            // pairing is a reach group now and never writes, so those stored
            // writes would otherwise outlive their cycle forever. Dropping them
            // on upgrade is what the old code did on removal after a restart.
            int total = 0;
            for (var worldEntry : section(root, "perWorld").entrySet()) {
                Map<Integer, String> map = new HashMap<>();
                readLocks(worldEntry.getValue(), map, Integer::parseInt, v1);
                if (worldEntry.getValue().isJsonArray()
                        && IPConfig.legacyCycleSlotsLocked()) {
                    Set<Integer> paired = pairedSlotsIn.apply(worldEntry.getKey());
                    if (map.keySet().removeAll(paired)) {
                        InventoryPlusClient.LOGGER.info("[locked-slots] {}: dropped the Column Cycler pairing's locks on {}",
                                worldEntry.getKey(), paired);
                    }
                }
                if (!map.isEmpty()) {
                    PLAYER.load(worldEntry.getKey(), map);
                    worlds.add(worldEntry.getKey());
                    total += map.size();
                }
            }
            // Ender locks live under a separate "enderPerWorld" section so their
            // 0-based slot indices don't collide with player slot indices.
            int enderTotal = loadIndexed(root, "enderPerWorld", ENDER, worlds, v1);
            // Created-slot locks (SlotKit) keyed by address text.
            // A key saved before MenuKit 6.0.0 is converted as it is read.
            int createdTotal = 0;
            for (var worldEntry : section(root, "createdPerWorld").entrySet()) {
                Map<String, String> map = new HashMap<>();
                readLocks(worldEntry.getValue(), map, CreatedSlotKey::migrate, v1);
                if (!map.isEmpty()) {
                    CREATED.load(worldEntry.getKey(), map);
                    worlds.add(worldEntry.getKey());
                    createdTotal += map.size();
                }
            }
            // Placed-container locks: world -> container key -> slot -> group.
            int containerTotal = 0;
            for (var worldEntry : section(root, "containersPerWorld").entrySet()) {
                Map<String, Map<Integer, String>> byKey = new HashMap<>();
                for (var c : worldEntry.getValue().getAsJsonObject().entrySet()) {
                    Map<Integer, String> map = new HashMap<>();
                    readLocks(c.getValue(), map, Integer::parseInt, v1);
                    if (!map.isEmpty()) { byKey.put(c.getKey(), map); containerTotal += map.size(); }
                }
                if (!byKey.isEmpty()) {
                    CONTAINERS.load(worldEntry.getKey(), byKey);
                    worlds.add(worldEntry.getKey());
                }
            }
            InventoryPlusClient.LOGGER.info(
                    "[locked-slots] loaded {} player + {} ender + {} created + {} container entries across {} world(s) from {}",
                    total, enderTotal, createdTotal, containerTotal, worlds.size(), path);
            if (v1[0]) {
                // The 1.5.x upgrade: every slot lock became a Slot lock above.
                // Rewrite now, so the file never has to be migrated twice.
                save();
                InventoryPlusClient.LOGGER.info("[locked-slots] upgraded {} from version 1: every lock is a Slot lock", path);
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException | NumberFormatException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-slots] failed to parse {}, starting with empty prefs",
                    path, e);
        }
    }

    private static JsonObject section(JsonObject root, String name) {
        return root.has(name) ? root.getAsJsonObject(name) : new JsonObject();
    }

    private static int loadIndexed(JsonObject root, String name, WorldStore<Map<Integer, String>> store,
                                   Set<String> worlds, boolean[] v1) {
        int total = 0;
        for (var worldEntry : section(root, name).entrySet()) {
            Map<Integer, String> map = new HashMap<>();
            readLocks(worldEntry.getValue(), map, Integer::parseInt, v1);
            if (!map.isEmpty()) {
                store.load(worldEntry.getKey(), map);
                worlds.add(worldEntry.getKey());
                total += map.size();
            }
        }
        return total;
    }

    /**
     * Reads one namespace's locks into {@code out}: a version 2 object of
     * target to group id, or a version 1 array of targets, each of which
     * becomes a Slot lock (and flags {@code v1} so the file is rewritten).
     */
    private static <K> void readLocks(com.google.gson.JsonElement json, Map<K, String> out,
                                      java.util.function.Function<String, K> key, boolean[] v1) {
        if (json.isJsonArray()) {
            v1[0] = true;
            for (var e : json.getAsJsonArray()) out.put(key.apply(e.getAsString()), Reach.SLOT_LOCK);
        } else {
            for (var e : json.getAsJsonObject().entrySet()) out.put(key.apply(e.getKey()), e.getValue().getAsString());
        }
    }

    /**
     * True if the given Slot is a lockable player slot (hotbar, main
     * inventory, armor or offhand).
     *
     * <h3>Why UUID equality and not reference equality</h3>
     *
     * In single-player, the integrated server runs in the same JVM as
     * the client. Vanilla {@link
     * net.minecraft.world.inventory.AbstractContainerMenu#moveItemStackTo}
     * is invoked on both threads (client predicts, server authoritative).
     * On the server thread, {@code slot.container} is {@code
     * ServerPlayer.getInventory()}, and on the client thread it's {@code
     * LocalPlayer.getInventory()}. Those are different Java objects for
     * the same logical player, so reference equality {@code slot.container
     * == mc.player.getInventory()} returns false on the server thread,
     * making any mixin gated on this check a no-op server-side. The client
     * then predicts "blocked", the server places, the client reverts, and
     * the slot flickers.
     *
     * <p>UUID equality is stable across the client/server divide because
     * both {@code LocalPlayer} and {@code ServerPlayer} carry the same
     * UUID for the same logical player.
     */
    public static boolean isLockable(Slot slot) {
        if (!(slot.container instanceof Inventory inv)) return false;
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci > MAX_PLAYER_CONTAINER_SLOT) return false;
        return isLocalPlayerInventory(inv);
    }

    /**
     * True if the given Slot is in the inventory and hotbar subset
     * (container-slot 0-35), the slots Column Cycler works on.
     *
     * <p>Uses UUID equality for the same reason as {@link #isLockable}: the
     * check must be stable on both the client and integrated-server threads.
     */
    public static boolean isInvOrHotbarSlot(Slot slot) {
        if (!(slot.container instanceof Inventory inv)) return false;
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci > MAX_INV_HOTBAR_CONTAINER_SLOT) return false;
        return isLocalPlayerInventory(inv);
    }

    /**
     * UUID-based check for "is this Inventory the local player's?". Stable
     * across the client/server divide in single-player (see {@link
     * #isLockable}).
     */
    private static boolean isLocalPlayerInventory(Inventory inv) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        return inv.player.getUUID().equals(mc.player.getUUID());
    }

    /**
     * The lock-state predicate every enforcement path calls
     * ({@code moveItemStackTo} wrap, the shift-click mixin, Sorting, Move
     * Matching, the render mixin). Dispatches by slot kind:
     *
     * <ul>
     *   <li>player-owned slot: IP's player namespace</li>
     *   <li>ender chest slot: IP's ender namespace</li>
     *   <li>created slot: IP's created namespace</li>
     *   <li>placed container IP can identify: IP's container namespace</li>
     * </ul>
     *
     * <p>Inventory Max's lock provider hook is gone since IP 2.0.0. Shared
     * container locks are planned to move into Inventory Plus later.
     *
     * <p>Returns false for unlockable slots and for slots nothing owns.
     */
    public static boolean isLockedSlot(Slot slot) {
        if (isLockable(slot)) {
            int cs = slot.getContainerSlot();
            return PLAYER.get().containsKey(cs);
        }
        if (isEnderSlot(slot)) return isEnderLocked(slot.getContainerSlot());
        if (isCreatedSlot(slot)) return isCreatedLocked(slot);
        if (isPlacedContainerSlot(slot)) return isContainerLocked(slot);
        return false;
    }

    /** True on the client render thread (where the open-screen UI and prediction run). */
    private static boolean isRenderThread() {
        return "Render thread".equals(Thread.currentThread().getName());
    }

    /**
     * True if {@code slot} can be locked at all, in any namespace. Used by the
     * {@code L} keybind (and its drag) to decide which slots respond. Includes
     * armor and offhand, which are lockable via {@code L} only.
     */
    public static boolean isLockableHere(Slot slot) {
        return isLockable(slot) || isEnderSlot(slot) || isCreatedSlot(slot)
                || isPlacedContainerSlot(slot);
    }

    /**
     * The id of the lock group stored on {@code slot}, or {@code null} when
     * nothing is stored there. Derived locks (a cycler's) are not stored here
     * and answer {@code null}.
     */
    public static @Nullable String storedGroup(Slot slot) {
        if (isLockable(slot)) return PLAYER.get().get(slot.getContainerSlot());
        if (isEnderSlot(slot)) return ENDER.get().get(slot.getContainerSlot());
        if (isCreatedSlot(slot)) {
            String key = CreatedSlotKey.of(slot);
            return key == null ? null : CREATED.get().get(key);
        }
        if (isPlacedContainerSlot(slot)) {
            String key = containerKeyFor(slot);
            Map<Integer, String> locks = key == null ? null : CONTAINERS.get().get(key);
            return locks == null ? null : locks.get(slot.getContainerSlot());
        }
        return null;
    }

    /**
     * {@code L} with a Slot-kind group selected: the same group already on the
     * slot comes off, and any other, or none, is replaced by {@code group}. Returns the group now on the slot,
     * or {@code null} if it came off.
     */
    public static @Nullable String applyGroup(Slot slot, String groupId) {
        String current = storedGroup(slot);
        String next = groupId.equals(current) ? null : groupId;
        setLockedSlot(slot, next);
        return next;
    }

    /** The group stored on player slot {@code containerSlotIndex}, or {@code null}. */
    public static @Nullable String playerStoredGroup(int containerSlotIndex) {
        return PLAYER.get().get(containerSlotIndex);
    }

    /**
     * Puts lock group {@code groupId} on {@code slot}, or takes its lock off
     * when {@code null}, routing to the right namespace.
     */
    public static void setLockedSlot(Slot slot, @Nullable String groupId) {
        if (isLockable(slot)) {
            put(PLAYER, slot.getContainerSlot(), groupId);
        } else if (isEnderSlot(slot)) {
            put(ENDER, slot.getContainerSlot(), groupId);
        } else if (isCreatedSlot(slot)) {
            String key = CreatedSlotKey.of(slot);
            if (key != null) put(CREATED, key, groupId);
        } else if (isPlacedContainerSlot(slot)) {
            String key = currentContainerKey();
            if (key == null) return;
            int index = slot.getContainerSlot();
            CONTAINERS.modify(byKey -> WorldStore.withEntry(byKey, key,
                    withLock(byKey.getOrDefault(key, Map.of()), index, groupId)));
        }
    }

    private static <K extends Comparable<? super K>> void put(WorldStore<Map<K, String>> store, K target,
                                                             @Nullable String groupId) {
        store.modify(map -> withLock(map, target, groupId));
    }

    /** {@code map} with {@code target} locked by {@code groupId}, or unlocked when {@code null}. */
    private static <K> Map<K, String> withLock(Map<K, String> map, K target, @Nullable String groupId) {
        return groupId == null ? WorldStore.withoutKey(map, target) : WorldStore.withEntry(map, target, groupId);
    }

    // Created slots (SlotKit; client-side, own namespace)

    /**
     * True if {@code slot} is a SlotKit created slot (a pocket, an
     * equipment slot). Render-thread-gated like the ender check: the identity
     * comes from MenuKit's client addressing, and the server thread has no
     * business consulting a client lock store.
     */
    public static boolean isCreatedSlot(Slot slot) {
        if (slot.container instanceof Inventory) return false;      // a player slot
        // Block-anchored containers are the placed-container namespace. Ruling
        // them out here keeps the created-slot probe off every chest slot on
        // the server thread, where a vanilla slot costs a caught exception.
        if (slot.container instanceof BlockEntity || slot.container instanceof CompoundContainer) return false;
        if (isEnderSlot(slot)) return false;
        return CreatedSlotKey.of(slot) != null;
    }

    public static boolean isCreatedLocked(Slot slot) {
        String key = CreatedSlotKey.of(slot);
        return key != null && CREATED.get().containsKey(key);
    }

    // Placed-container slots (client-side, per player, own namespace)

    /**
     * The persistent key of the container the open menu shows, cached per
     * containerId. Null when nothing identifiable is open. The first resolution
     * for a menu also prunes indices the container cannot hold (it shrank, or
     * a different block now stands there).
     */
    private static @Nullable String currentContainerKey() {
        if (!isRenderThread()) return null;   // the server thread reads the block entity, see containerKeyFor
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) return null;
        if (menu != cachedMenu) {
            ContainerIdentity id = ContainerIdentity.forMenu(menu);
            String key = (id != null && id.isPersistent()) ? id.key() : null;
            // Asked before the screen finished init (a container list places its
            // slots then, and their locks are read), the block may not be tied to
            // the menu yet: answer null for now, and ask again, rather than keep
            // a null for the whole life of the menu and lose its locks.
            if (key == null && !com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker.settled(menu)) {
                return null;
            }
            cachedMenu = menu;
            cachedContainerKey = key;
            // [reach-probe]: this key is kept for the whole life of the menu.
            InventoryPlusClient.LOGGER.info("[reach-probe] container key for menu {}: {} (tracker pos {})",
                    menu.containerId, cachedContainerKey,
                    com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker.getBlockPos(menu.containerId));
            if (cachedContainerKey != null) pruneToCapacity(cachedContainerKey, menu);
        }
        return cachedContainerKey;
    }

    /** Drop locked indices at or past the open container's size, and the key if that empties it. */
    private static void pruneToCapacity(String key, AbstractContainerMenu menu) {
        int size = -1;
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) { size = s.container.getContainerSize(); break; }
        }
        if (size < 0) return;
        final int cap = size;
        Map<Integer, String> locked = CONTAINERS.get().get(key);
        if (locked == null) return;   // nothing locked in this container, nothing to prune
        Map<Integer, String> kept = new HashMap<>();
        locked.forEach((i, group) -> { if (i < cap) kept.put(i, group); });
        if (kept.size() == locked.size()) return;
        // freezeContainers drops the key entirely if nothing was kept.
        CONTAINERS.modify(byKey -> WorldStore.withEntry(byKey, key, kept));
        InventoryPlusClient.LOGGER.info("[locked-slots] pruned {} container lock(s) at {} (capacity {})",
                locked.size() - kept.size(), key, cap);
    }

    /** Forget every lock on the container at {@code pos}. Called when the local player breaks it. */
    public static void pruneContainerAt(String key) {
        // Called on every block the local player breaks. In 1.5.0 to 1.6.0 this
        // removed from a read-only view that was Map.of() in any world with no
        // container locks, and crashed. Writing through the store cannot: the
        // modifier builds a new value, and an unchanged value is not saved.
        Map<String, Map<Integer, String>> previous = CONTAINERS.modify(byKey -> WorldStore.withoutKey(byKey, key));
        if (previous.containsKey(key)) {
            InventoryPlusClient.LOGGER.info("[locked-slots] container broken, dropped its locks: {}", key);
            if (key.equals(cachedContainerKey)) cachedContainerKey = null;
        }
    }

    /**
     * True if {@code slot} belongs to an identifiable placed container on the
     * open screen. Render-thread-gated like ender: identity comes from the
     * client's open-click tracker. Excludes player slots (a real
     * {@link Inventory}) and the ender chest, which have their own namespaces.
     */
    public static boolean isPlacedContainerSlot(Slot slot) {
        if (slot.container instanceof Inventory) return false;
        if (isEnderSlot(slot)) return false;
        return containerKeyFor(slot) != null;
    }

    /** [reach-probe]: the container key this thread judges {@code slot} by, or "-" when it is not a placed container's. */
    public static String probeContainerKey(Slot slot) {
        if (!isPlacedContainerSlot(slot)) return "-";
        String key = containerKeyFor(slot);
        return key == null ? "none" : key;
    }

    /**
     * The key of the container {@code slot} belongs to, on either thread. The
     * render thread uses the cached open-menu key, since the client's menu has
     * no block identity of its own. The server thread reads the real block
     * entity. Both canonicalise a double chest identically, so the two threads
     * cannot disagree about which container a slot is in. (1.5.0 had no
     * server-thread answer here at all. 1.5.1 fixed that.)
     */
    private static @Nullable String containerKeyFor(Slot slot) {
        return isRenderThread() ? currentContainerKey()
                                : ContainerIdentity.keyForContainer(slot.container);
    }

    public static boolean isContainerLocked(Slot slot) {
        String key = containerKeyFor(slot);
        if (key == null) return false;
        Map<Integer, String> locks = CONTAINERS.get().get(key);
        return locks != null && locks.containsKey(slot.getContainerSlot());
    }

    // Ender chest slots (client-side, per player, own namespace)

    /**
     * True if {@code slot} is one of the local player's ender chest slots.
     * Ender is inherently single-viewer (you can only ever open your own), so
     * no local-player UUID check is needed: any {@link PlayerEnderChestContainer}
     * on screen is yours. (Contrast {@link #isLockable}, which must guard against
     * a placed container that merely looks like player inventory.)
     */
    public static boolean isEnderSlot(Slot slot) {
        // Server or singleplayer integrated server: the real ender container.
        if (slot.container instanceof PlayerEnderChestContainer) {
            int ci = slot.getContainerSlot();
            return ci >= 0 && ci < ENDER_SLOT_COUNT;
        }
        // Client: the open ender menu wraps a generic SimpleContainer (same as a
        // placed chest), so the ender chest is identified by the block the player
        // opened. Render-thread-gated: the integrated-server thread sees the real
        // PlayerEnderChestContainer above and must not consult client open-screen
        // state.
        if (!isRenderThread()) return false;
        if (slot.container instanceof Inventory) return false;   // a player slot
        // A slot group's slot (a box in the ender chest's container list) is its
        // own container, not the ender chest's, whatever its index.
        if (CreatedSlotKey.of(slot) != null) return false;
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci >= ENDER_SLOT_COUNT) return false;
        return ContainerOpenTracker.openContainerBlock() instanceof EnderChestBlock;
    }

    /** True if the given ender slot index is locked in the current world. */
    public static boolean isEnderLocked(int enderSlotIndex) {
        return ENDER.get().containsKey(enderSlotIndex);
    }

    // Every world at once (a group's delete, the Lock groups Reset)

    /**
     * Counts the stored slot locks of group {@code groupId} (every group when
     * {@code null}) in every world, adding the worlds that hold any to
     * {@code worlds}. For the confirms that state what a change removes.
     */
    public static int count(@Nullable String groupId, Set<String> worlds) {
        int[] n = {0};
        java.util.function.BiConsumer<String, Map<?, String>> tally = (world, locks) -> {
            int before = n[0];
            for (String g : locks.values()) if (groupId == null || groupId.equals(g)) n[0]++;
            if (n[0] > before) worlds.add(world);
        };
        PLAYER.forEachWorld(tally::accept);
        ENDER.forEachWorld(tally::accept);
        CREATED.forEachWorld(tally::accept);
        CONTAINERS.forEachWorld((world, byKey) -> byKey.values().forEach(locks -> tally.accept(world, locks)));
        return n[0];
    }

    /**
     * Moves every stored lock of group {@code from} to group {@code to} in
     * every world, or removes them when {@code to} is {@code null}. Every
     * lock when {@code from} is {@code null} too.
     */
    public static void reassign(@Nullable String from, @Nullable String to) {
        PLAYER.replaceAll((world, locks) -> reassigned(locks, from, to));
        ENDER.replaceAll((world, locks) -> reassigned(locks, from, to));
        CREATED.replaceAll((world, locks) -> reassigned(locks, from, to));
        CONTAINERS.replaceAll((world, byKey) -> {
            Map<String, Map<Integer, String>> out = new HashMap<>();
            byKey.forEach((key, locks) -> out.put(key, reassigned(locks, from, to)));
            return out;
        });
    }

    private static <K> Map<K, String> reassigned(Map<K, String> locks, @Nullable String from, @Nullable String to) {
        Map<K, String> out = new HashMap<>();
        locks.forEach((target, group) -> {
            boolean match = from == null || from.equals(group);
            if (!match) out.put(target, group);
            else if (to != null) out.put(target, to);
        });
        return out;
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            root.add("perWorld", writeWorlds(PLAYER));
            root.add("enderPerWorld", writeWorlds(ENDER));
            root.add("createdPerWorld", writeWorlds(CREATED));
            JsonObject containersPerWorld = new JsonObject();
            CONTAINERS.forEachWorld((world, containers) -> {
                JsonObject byKey = new JsonObject();
                containers.forEach((key, slots) -> byKey.add(key, writeLocks(slots)));
                containersPerWorld.add(world, byKey);
            });
            root.add("containersPerWorld", containersPerWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-slots] failed to write {}, change won't survive a restart",
                    path, e);
        }
    }

    private static <K> JsonObject writeWorlds(WorldStore<Map<K, String>> store) {
        JsonObject perWorld = new JsonObject();
        store.forEachWorld((world, locks) -> perWorld.add(world, writeLocks(locks)));
        return perWorld;
    }

    private static <K> JsonObject writeLocks(Map<K, String> locks) {
        JsonObject out = new JsonObject();
        locks.forEach((target, group) -> out.addProperty(String.valueOf(target), group));
        return out;
    }
}
