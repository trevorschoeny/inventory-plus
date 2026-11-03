package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.api.WorldStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;
import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;
import com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker;

import com.trevlar.slotkit.api.Presence;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Where slot locks are kept on the client, and which slot is locked.
 *
 * <h3>Two homes</h3>
 *
 * <ul>
 *   <li><b>The player's own slots</b>: hotbar (container-slot 0-8), main inventory
 *       (9-35), armor (36-39), offhand (40), the ender chest, and SlotKit created
 *       slots (pockets, equipment). Kept here, per world, so they work on any server.
 *       Ephemeral slots (crafting input and result, anvil inputs) are not lockable.</li>
 *   <li><b>A placed container's slots</b>: SHARED slot state on its block entity
 *       ({@link SlotLocks#CHANNEL}), so they hold for every player and for hoppers,
 *       droppers and copper golems. Needs Inventory Plus on the server; without it a
 *       chest's slots can't be locked.</li>
 * </ul>
 *
 * <h3>Persistence (per world)</h3>
 *
 * File: {@code config/inventoryplus/locked-slots.json}. Version 3:
 *
 * <pre>{@code
 * {
 *   "version": 3,
 *   "perWorld": { "singleplayer:New World": ["9", "36"] },
 *   "enderPerWorld": { ... }, "createdPerWorld": { "<world>": ["<created slot key>"] },
 *   "containersPerWorld": { "<world>": { "<container key>": ["4"] } }
 * }
 * }</pre>
 *
 * <p>{@code containersPerWorld} is what 3.3.x and earlier kept for chests, each
 * player's own. It is only read now: those locks still hold where they are, and the
 * first time a chest is opened on a server with Inventory Plus they move onto the
 * chest itself and leave the file ({@link #currentContainerKey}).
 *
 * <p>Version 2 named a lock group per lock, and version 1 stored bare arrays. Both
 * load: every lock, whatever its group, is a lock now. That is the lock groups
 * migration. The file is rewritten as version 3 at once.
 */
public final class LockedSlots {

    private LockedSlots() {}

    private static final int CURRENT_VERSION = 3;

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

    // The lock namespaces. Each is a WorldStore: one immutable value per world,
    // read without ever creating anything, written only through modify, and saved
    // only when a write actually changes something. See WorldStore for why.

    /** Player-owned slot locks (inventory, hotbar, armor, offhand), by container-slot index. */
    private static final WorldStore<Set<Integer>> PLAYER = WorldStore.sortedSet(LockedSlots::save);

    /**
     * Ender chest slot locks. Separate from {@link #PLAYER} because ender slot indices
     * (0-26) overlap player container-slot indices. The ender chest is the player's
     * own, so it stays here like the inventory, and works on a vanilla server.
     */
    private static final WorldStore<Set<Integer>> ENDER = WorldStore.sortedSet(LockedSlots::save);

    /**
     * Created-slot keys ({@link CreatedSlotKey}): SlotKit slots (pockets, equipment).
     * Own namespace, because a created slot's container index collides with vanilla's.
     */
    private static final WorldStore<Set<String>> CREATED = WorldStore.sortedSet(LockedSlots::save);

    /**
     * Container identity ({@code block:<dim>:<x>,<y>,<z>}, see {@link ContainerIdentity})
     * to locked slot indices: the per-player chest locks of 3.3.x and earlier. Read
     * and honored, never added to, emptied as they move onto their chests.
     */
    private static final WorldStore<Map<String, Set<Integer>>> CONTAINERS =
            WorldStore.of(Collections.emptySortedMap(), LockedSlots::freezeContainers, LockedSlots::save);

    /** The immutable form of one world's old chest locks; a chest with none left is dropped. */
    private static Map<String, Set<Integer>> freezeContainers(Map<String, Set<Integer>> byKey) {
        TreeMap<String, Set<Integer>> out = new TreeMap<>();
        byKey.forEach((key, slots) -> {
            if (!slots.isEmpty()) out.put(key, Collections.unmodifiableSortedSet(new TreeSet<>(slots)));
        });
        return Collections.unmodifiableSortedMap(out);
    }

    // Per-open-screen cache of the container key. isLockedSlot runs per slot per
    // frame; resolving the identity re-reads the tracker and a block state, so it
    // is done once per menu and reused until the menu changes. Keyed by the menu
    // itself: container ids start again in each world.
    private static @Nullable AbstractContainerMenu cachedMenu;
    private static @Nullable String cachedContainerKey;

    private static boolean loaded = false;

    /**
     * Reads the file. {@code pairedSlotsIn} names, per world, the player slots the
     * Column Cycler's old pairing locked; consulted only when upgrading a version 1
     * file, whose pairing writes are dropped (see the body).
     */
    public static void load(Function<String, Set<Integer>> pairedSlotsIn) {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            int total = 0;
            for (var worldEntry : section(root, "perWorld").entrySet()) {
                Set<Integer> locks = readLocks(worldEntry.getValue(), Integer::parseInt);
                // 1.5.x: "Lock the slots the cyclers use" wrote a real lock onto each
                // cycle slot and removed it again only within one screen session. The
                // pairing never writes now, so those stored writes would outlive their
                // cycle forever; they are dropped on upgrade from version 1.
                if (version < 2 && IPConfig.legacyCycleSlotsLocked()) {
                    Set<Integer> paired = pairedSlotsIn.apply(worldEntry.getKey());
                    if (locks.removeAll(paired)) {
                        InventoryPlusClient.LOGGER.info("[locked-slots] {}: dropped the Column Cycler pairing's locks on {}",
                                worldEntry.getKey(), paired);
                    }
                }
                if (!locks.isEmpty()) {
                    PLAYER.load(worldEntry.getKey(), locks);
                    total += locks.size();
                }
            }
            int ender = 0;
            for (var worldEntry : section(root, "enderPerWorld").entrySet()) {
                Set<Integer> locks = readLocks(worldEntry.getValue(), Integer::parseInt);
                if (!locks.isEmpty()) { ENDER.load(worldEntry.getKey(), locks); ender += locks.size(); }
            }
            int created = 0;
            for (var worldEntry : section(root, "createdPerWorld").entrySet()) {
                // A key saved before MenuKit 6.0.0 is converted as it is read.
                Set<String> locks = readLocks(worldEntry.getValue(), CreatedSlotKey::migrate);
                if (!locks.isEmpty()) { CREATED.load(worldEntry.getKey(), locks); created += locks.size(); }
            }
            int containers = 0;
            for (var worldEntry : section(root, "containersPerWorld").entrySet()) {
                Map<String, Set<Integer>> byKey = new HashMap<>();
                for (var c : worldEntry.getValue().getAsJsonObject().entrySet()) {
                    Set<Integer> locks = readLocks(c.getValue(), Integer::parseInt);
                    if (!locks.isEmpty()) { byKey.put(c.getKey(), locks); containers += locks.size(); }
                }
                if (!byKey.isEmpty()) CONTAINERS.load(worldEntry.getKey(), byKey);
            }
            InventoryPlusClient.LOGGER.info(
                    "[locked-slots] loaded {} player + {} ender + {} created + {} old chest locks from {}",
                    total, ender, created, containers, path);
            if (version < CURRENT_VERSION) {
                // Rewritten now, so the file is never migrated twice.
                save();
                InventoryPlusClient.LOGGER.info("[locked-slots] upgraded {} from version {}: every lock group's lock is a lock",
                        path, version);
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException | NumberFormatException e) {
            InventoryPlusClient.LOGGER.error("[locked-slots] failed to parse {}, starting with no locks", path, e);
        }
    }

    private static JsonObject section(JsonObject root, String name) {
        return root.has(name) ? root.getAsJsonObject(name) : new JsonObject();
    }

    /**
     * One namespace's locks, in any version: an array of targets (versions 1 and 3), or
     * an object of target to lock group id (version 2), where every group is a lock now.
     */
    public static <K> Set<K> readLocks(JsonElement json, Function<String, K> key) {
        Set<K> out = new HashSet<>();
        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) out.add(key.apply(e.getAsString()));
        } else {
            for (String target : json.getAsJsonObject().keySet()) out.add(key.apply(target));
        }
        return out;
    }

    /**
     * True if the given Slot is a lockable player slot (hotbar, main inventory, armor
     * or offhand) of the local player. Matched by UUID, not by reference: in single
     * player the integrated server's {@code ServerPlayer} inventory is a different
     * object for the same player, and a check that missed it would let the client
     * predict "blocked" while the server places, and the slot would flicker.
     */
    public static boolean isLockable(Slot slot) {
        if (!(slot.container instanceof Inventory inv)) return false;
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci > MAX_PLAYER_CONTAINER_SLOT) return false;
        return isLocalPlayerInventory(inv);
    }

    /** True if the given Slot is in the inventory and hotbar subset (container-slot 0-35), the slots Column Cycler works on. */
    public static boolean isInvOrHotbarSlot(Slot slot) {
        if (!(slot.container instanceof Inventory inv)) return false;
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci > MAX_INV_HOTBAR_CONTAINER_SLOT) return false;
        return isLocalPlayerInventory(inv);
    }

    private static boolean isLocalPlayerInventory(Inventory inv) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        return inv.player.getUUID().equals(mc.player.getUUID());
    }

    /** True on the client render thread (where the open-screen UI and prediction run). */
    private static boolean isRenderThread() {
        return "Render thread".equals(Thread.currentThread().getName());
    }

    // Reading

    /**
     * Whether {@code slot} is locked, in whichever home its lock lives: the player's
     * own namespaces, an old per-player chest lock, or the container's SHARED lock.
     * Not paused by the locks switch; callers that draw or judge ask that first.
     */
    public static boolean isLockedSlot(Slot slot) {
        return isOwnLocked(slot) || !(slot.container instanceof Inventory) && SlotLocks.CHANNEL.get(slot);
    }

    /**
     * Whether {@code slot} carries a lock this client keeps: one of the player's own
     * slots, or a chest slot with an old per-player lock. What the client's own veto
     * judges; a SHARED lock is judged by {@link SlotLocks}'s, on both sides.
     */
    public static boolean isOwnLocked(Slot slot) {
        if (isLockable(slot)) return PLAYER.get().contains(slot.getContainerSlot());
        if (isEnderSlot(slot)) return ENDER.get().contains(slot.getContainerSlot());
        if (isCreatedSlot(slot)) {
            String key = CreatedSlotKey.of(slot);
            return key != null && CREATED.get().contains(key);
        }
        if (isPlacedContainerSlot(slot)) {
            String key = containerKeyFor(slot);
            Set<Integer> locks = key == null ? null : CONTAINERS.get().get(key);
            return locks != null && locks.contains(slot.getContainerSlot());
        }
        return false;
    }

    /** Whether the player's own inventory slot {@code containerSlot} is locked, for a slot reached off-menu (a pickup). */
    public static boolean isPlayerLocked(int containerSlot) {
        return PLAYER.get().contains(containerSlot);
    }

    /** True if the given ender slot index is locked in the current world. */
    public static boolean isEnderLocked(int enderSlotIndex) {
        return ENDER.get().contains(enderSlotIndex);
    }

    /**
     * True if {@code slot} can be locked here: one of the player's own, or a placed
     * container's when the server keeps container locks. Used by {@code L} and its drag.
     */
    public static boolean isLockableHere(Slot slot) {
        if (isLockable(slot) || isEnderSlot(slot) || isCreatedSlot(slot)) return true;
        return isPlacedContainerSlot(slot) && Presence.serverHasChannel(SlotLocks.ID);
    }

    // Writing

    /** {@code L}: locks {@code slot}, or unlocks it if locked. Returns whether it is locked now. */
    public static boolean toggle(Slot slot) {
        boolean next = !isLockedSlot(slot);
        setLocked(slot, next);
        return next;
    }

    /** Locks or unlocks {@code slot}, in the home its kind of slot keeps locks in. */
    public static void setLocked(Slot slot, boolean locked) {
        if (isLockable(slot)) {
            PLAYER.modify(set -> WorldStore.withElement(set, slot.getContainerSlot(), locked));
        } else if (isEnderSlot(slot)) {
            ENDER.modify(set -> WorldStore.withElement(set, slot.getContainerSlot(), locked));
        } else if (isCreatedSlot(slot)) {
            String key = CreatedSlotKey.of(slot);
            if (key != null) CREATED.modify(set -> WorldStore.withElement(set, key, locked));
        } else if (isPlacedContainerSlot(slot)) {
            // An old per-player lock comes off here too, so unlocking always unlocks.
            String key = containerKeyFor(slot);
            if (!locked && key != null) dropOldLock(key, slot.getContainerSlot());
            SlotLocks.CHANNEL.set(slot, locked);
        }
    }

    private static void dropOldLock(String key, int index) {
        CONTAINERS.modify(byKey -> {
            Set<Integer> locks = byKey.get(key);
            if (locks == null || !locks.contains(index)) return byKey;
            return WorldStore.withEntry(byKey, key, WorldStore.withElement(locks, index, false));
        });
    }

    // Created slots (SlotKit; client-side, own namespace)

    /**
     * True if {@code slot} is a SlotKit created slot (a pocket, an equipment slot).
     * The identity comes from MenuKit's client addressing.
     */
    public static boolean isCreatedSlot(Slot slot) {
        if (slot.container instanceof Inventory) return false;      // a player slot
        // Block-anchored containers are the placed-container namespace. Ruling them
        // out here keeps the created-slot probe off every chest slot on the server
        // thread, where a vanilla slot costs a caught exception.
        if (slot.container instanceof BlockEntity || slot.container instanceof CompoundContainer) return false;
        if (isEnderSlot(slot)) return false;
        return CreatedSlotKey.of(slot) != null;
    }

    // Placed-container slots

    /**
     * The persistent key of the container the open menu shows, cached per menu. Null
     * when nothing identifiable is open. The first resolution for a menu moves that
     * chest's old per-player locks onto the chest, when the server keeps them.
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
            // the menu yet: answer null for now, and ask again, rather than keep a
            // null for the whole life of the menu and lose its locks.
            if (key == null && !ContainerOpenTracker.settled(menu)) return null;
            cachedMenu = menu;
            cachedContainerKey = key;
            if (key != null) moveOldLocks(key, menu);
        }
        return cachedContainerKey;
    }

    /**
     * The lock migration for chests: an old per-player lock on this chest becomes the
     * chest's own SHARED lock, then leaves the file. Only with Inventory Plus on the
     * server; on a vanilla server the old locks stay and keep holding for this player.
     * Indices past the container's size (it shrank, or another block stands there) go.
     */
    private static void moveOldLocks(String key, AbstractContainerMenu menu) {
        Set<Integer> old = CONTAINERS.get().get(key);
        if (old == null || !Presence.serverHasChannel(SlotLocks.ID)) return;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory || !old.contains(s.getContainerSlot())) continue;
            SlotLocks.CHANNEL.set(s, true);
        }
        CONTAINERS.modify(byKey -> WorldStore.withoutKey(byKey, key));
        InventoryPlusClient.LOGGER.info("[locked-slots] moved {} lock(s) onto the container at {}", old.size(), key);
    }

    /** Forget every old lock on the container at {@code key}. Called when the local player breaks it. */
    public static void pruneContainerAt(String key) {
        Map<String, Set<Integer>> previous = CONTAINERS.modify(byKey -> WorldStore.withoutKey(byKey, key));
        if (previous.containsKey(key)) {
            InventoryPlusClient.LOGGER.info("[locked-slots] container broken, dropped its locks: {}", key);
            if (key.equals(cachedContainerKey)) cachedContainerKey = null;
        }
    }

    /**
     * True if {@code slot} belongs to an identifiable placed container on the open
     * screen. Excludes player slots and the ender chest, which have their own namespaces.
     */
    public static boolean isPlacedContainerSlot(Slot slot) {
        if (slot.container instanceof Inventory) return false;
        if (isEnderSlot(slot)) return false;
        return containerKeyFor(slot) != null;
    }

    /**
     * The key of the container {@code slot} belongs to, on either thread. The render
     * thread uses the cached open-menu key, since the client's menu has no block
     * identity of its own. The server thread reads the real block entity. Both
     * canonicalise a double chest identically.
     */
    private static @Nullable String containerKeyFor(Slot slot) {
        return isRenderThread() ? currentContainerKey() : ContainerIdentity.keyForContainer(slot.container);
    }

    // Ender chest slots (client-side, per player, own namespace)

    /**
     * True if {@code slot} is one of the local player's ender chest slots. Only ever
     * your own ender chest is on screen, so no UUID check is needed.
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
        // PlayerEnderChestContainer above.
        if (!isRenderThread()) return false;
        if (slot.container instanceof Inventory) return false;   // a player slot
        // A slot group's slot (a box in the ender chest's container list) is its
        // own container, not the ender chest's, whatever its index.
        if (CreatedSlotKey.of(slot) != null) return false;
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci >= ENDER_SLOT_COUNT) return false;
        return ContainerOpenTracker.openContainerBlock() instanceof EnderChestBlock;
    }

    // Every world at once (the Locks tab's Reset)

    /** Counts the locks kept on this computer in every world, adding the worlds that hold any to {@code worlds}. */
    public static int count(Set<String> worlds) {
        int[] n = {0};
        java.util.function.BiConsumer<String, Integer> tally = (world, k) -> {
            if (k > 0) { n[0] += k; worlds.add(world); }
        };
        PLAYER.forEachWorld((w, locks) -> tally.accept(w, locks.size()));
        ENDER.forEachWorld((w, locks) -> tally.accept(w, locks.size()));
        CREATED.forEachWorld((w, locks) -> tally.accept(w, locks.size()));
        CONTAINERS.forEachWorld((w, byKey) -> byKey.values().forEach(locks -> tally.accept(w, locks.size())));
        return n[0];
    }

    /** Removes every lock kept on this computer, in every world. A chest's or box's own locks stay on it. */
    public static void clearAll() {
        PLAYER.replaceAll((w, locks) -> null);
        ENDER.replaceAll((w, locks) -> null);
        CREATED.replaceAll((w, locks) -> null);
        CONTAINERS.replaceAll((w, byKey) -> null);
    }

    // Persistence

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
            InventoryPlusClient.LOGGER.error("[locked-slots] failed to write {}, change won't survive a restart", path, e);
        }
    }

    private static <K> JsonObject writeWorlds(WorldStore<Set<K>> store) {
        JsonObject perWorld = new JsonObject();
        store.forEachWorld((world, locks) -> perWorld.add(world, writeLocks(locks)));
        return perWorld;
    }

    private static <K> JsonArray writeLocks(Set<K> locks) {
        JsonArray out = new JsonArray();
        for (K target : locks) out.add(String.valueOf(target));
        return out;
    }
}
