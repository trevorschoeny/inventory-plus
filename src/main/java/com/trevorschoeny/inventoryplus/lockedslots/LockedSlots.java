package com.trevorschoeny.inventoryplus.lockedslots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;
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
import java.util.function.IntPredicate;

/**
 * Central state + persistence for Locked Slots.
 *
 * <h3>Scope (per spec)</h3>
 *
 * Player-side slots only — hotbar (container-slot 0-8), main inventory
 * (9-35), armor (36-39), offhand (40). Ephemeral slots (crafting input
 * / result, anvil inputs, etc.) are not lockable.
 *
 * <h3>Persistence (per-world per Trev 2026-05-16)</h3>
 *
 * File: {@code config/inventoryplus/locked-slots.json}. Schema:
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "perWorld": {
 *     "singleplayer:New World": [9, 36, 40],
 *     "server:my.minecraft.server": [10, 11]
 *   }
 * }
 * }</pre>
 */
public final class LockedSlots {

    private LockedSlots() {}

    private static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Inclusive upper bound on player container-slot indices we support locking on. */
    public static final int MAX_PLAYER_CONTAINER_SLOT = 40;

    /** Inclusive upper bound on the "inv + hotbar" subset (the part that gets the edit-mode overlay). */
    public static final int MAX_INV_HOTBAR_CONTAINER_SLOT = 35;

    /**
     * Ender chest slot count — container-slot 0-26. Ender locks live in their
     * own per-world namespace ({@link #PER_WORLD_ENDER}) so ender slot N does
     * not collide with player container-slot N (both are 0-based).
     */
    public static final int ENDER_SLOT_COUNT = 27;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("locked-slots.json");
    }

    /** Player-owned slot locks (inv / hotbar / armor / offhand), keyed by world. */
    private static final Map<String, Set<Integer>> PER_WORLD = new HashMap<>();

    /**
     * Ender chest slot locks, keyed by world. Separate from {@link #PER_WORLD}
     * because ender slot indices (0-26) overlap player container-slot indices.
     * Ender is single-viewer (your own ender, no other player can open it), so
     * it stays client-side per-player just like player slots (§0005 — anything
     * client-doable stays in IP; also works on a vanilla server without IPP).
     */
    private static final Map<String, Set<Integer>> PER_WORLD_ENDER = new HashMap<>();
    /**
     * worldId -> created-slot keys ({@link CreatedSlotKey}). MenuKit: Containers
     * slots (pockets, equipment) locked by the player. Own namespace, like ender,
     * because a created slot's container index collides with vanilla's.
     * Trev 2026-09-07: created slots are treated exactly like vanilla slots for
     * every lock feature.
     */
    private static final Map<String, Set<String>> PER_WORLD_CREATED = new HashMap<>();
    /**
     * worldId -> container identity ({@code block:<dim>:<x>,<y>,<z>}, see
     * {@link ContainerIdentity}) -> locked slot indices. Placed-container locks,
     * client-side and per player (Trev 2026-09-07: intentional reversal of
     * Inventory Max's shared model; IP locks them for you). Claimed BEFORE any
     * downstream provider, so IM's shared channel no longer answers for placed
     * containers while IP is present.
     */
    private static final Map<String, Map<String, Set<Integer>>> PER_WORLD_CONTAINERS = new HashMap<>();

    // Per-open-screen cache of the container key. isLockedSlot runs per slot per
    // frame; resolving the identity re-reads the tracker and a block state, so
    // it is done once per containerId and reused until the menu changes.
    private static int cachedContainerId = Integer.MIN_VALUE;
    private static @Nullable String cachedContainerKey;

    // ── Downstream lock providers (the SlotLockProvider seam) ───────────────
    //
    // IP locks its own client-side slots (player + ender) directly. Placed-
    // container locks are shared and live in MenuKit's server-authoritative
    // channel, which IP (client-only) can't reach — so IPP registers a provider
    // here at its init and the unified predicates below dispatch container slots
    // to it. IP itself registers nothing.
    private static final List<SlotLockProvider> PROVIDERS = new ArrayList<>();

    // ── Derived player-slot locks ───────────────────────────────────────
    //
    // Some features lock player slots without owning an entry in the stored
    // set: the lock is derived from feature state and must appear and vanish
    // the instant that state or its config changes, with nothing to migrate.
    // Hotbar Cycler's toggled rows are the first case (see hotbar-cycler.md:
    // the lock is positional, stays put across a rotation, and is governed by
    // two configs at once).
    //
    // A seam rather than a direct call because the alternative inverts the
    // package dependency: hotbarcycler already imports this package, so
    // importing it back would close a cycle. Features register themselves at
    // init and this class stays ignorant of them, the same shape as
    // SlotLockProvider above.
    private static final List<IntPredicate> DERIVED_PLAYER_LOCKS = new ArrayList<>();

    /**
     * Registers a predicate over player container-slot indices that reports
     * additional locked slots. Consulted by {@link #isLockedSlot(Slot)} on top
     * of the stored set; it never writes, so the player's manual locks are
     * untouched and a slot stops being derived-locked as soon as the feature
     * says so.
     */
    public static void registerDerivedPlayerLock(IntPredicate predicate) {
        if (predicate == null || DERIVED_PLAYER_LOCKS.contains(predicate)) return;
        DERIVED_PLAYER_LOCKS.add(predicate);
    }

    /** True if any registered feature derives a lock for this player slot. */
    private static boolean isDerivedLocked(int containerSlotIndex) {
        for (IntPredicate p : DERIVED_PLAYER_LOCKS) {
            if (p.test(containerSlotIndex)) return true;
        }
        return false;
    }

    /** Registers a downstream provider (called by IPP at mod init). */
    public static void registerProvider(SlotLockProvider provider) {
        PROVIDERS.add(provider);
        InventoryPlusClient.LOGGER.info(
                "[locked-slots] registered slot-lock provider {} ({} total)",
                provider.getClass().getSimpleName(), PROVIDERS.size());
    }

    /** First registered provider that owns this slot, or null if none does. */
    private static @Nullable SlotLockProvider providerFor(Slot slot) {
        for (SlotLockProvider p : PROVIDERS) {
            if (p.handles(slot)) return p;
        }
        return null;
    }

    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject perWorld = root.has("perWorld")
                    ? root.getAsJsonObject("perWorld")
                    : new JsonObject();
            int total = 0;
            for (var worldEntry : perWorld.entrySet()) {
                String worldId = worldEntry.getKey();
                JsonArray arr = worldEntry.getValue().getAsJsonArray();
                Set<Integer> set = new HashSet<>();
                for (var slot : arr) set.add(slot.getAsInt());
                if (!set.isEmpty()) {
                    PER_WORLD.put(worldId, set);
                    total += set.size();
                }
            }
            // Ender locks live under a separate "enderPerWorld" section so their
            // 0-based slot indices don't collide with player slot indices. Absent
            // in pre-ender files — that just reads as zero ender locks.
            JsonObject enderPerWorld = root.has("enderPerWorld")
                    ? root.getAsJsonObject("enderPerWorld")
                    : new JsonObject();
            int enderTotal = 0;
            for (var worldEntry : enderPerWorld.entrySet()) {
                String worldId = worldEntry.getKey();
                JsonArray arr = worldEntry.getValue().getAsJsonArray();
                Set<Integer> set = new HashSet<>();
                for (var slot : arr) set.add(slot.getAsInt());
                if (!set.isEmpty()) {
                    PER_WORLD_ENDER.put(worldId, set);
                    enderTotal += set.size();
                }
            }
            // Created-slot locks (MenuKit: Containers) live under "createdPerWorld"
            // as opaque CreatedSlotKey strings. Absent in older files.
            JsonObject createdPerWorld = root.has("createdPerWorld")
                    ? root.getAsJsonObject("createdPerWorld")
                    : new JsonObject();
            int createdTotal = 0;
            for (var worldEntry : createdPerWorld.entrySet()) {
                Set<String> set = new HashSet<>();
                for (var key : worldEntry.getValue().getAsJsonArray()) set.add(key.getAsString());
                if (!set.isEmpty()) {
                    PER_WORLD_CREATED.put(worldEntry.getKey(), set);
                    createdTotal += set.size();
                }
            }
            // Placed-container locks: world -> container key -> [slot indices].
            JsonObject containersPerWorld = root.has("containersPerWorld")
                    ? root.getAsJsonObject("containersPerWorld") : new JsonObject();
            int containerTotal = 0;
            for (var worldEntry : containersPerWorld.entrySet()) {
                Map<String, Set<Integer>> byKey = new HashMap<>();
                for (var c : worldEntry.getValue().getAsJsonObject().entrySet()) {
                    Set<Integer> set = new HashSet<>();
                    for (var i : c.getValue().getAsJsonArray()) set.add(i.getAsInt());
                    if (!set.isEmpty()) { byKey.put(c.getKey(), set); containerTotal += set.size(); }
                }
                if (!byKey.isEmpty()) PER_WORLD_CONTAINERS.put(worldEntry.getKey(), byKey);
            }
            InventoryPlusClient.LOGGER.info(
                    "[locked-slots] loaded {} player + {} ender + {} created + {} container entries across {} world(s) from {}",
                    total, enderTotal, createdTotal, containerTotal, PER_WORLD.size(), path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-slots] failed to parse {} — starting with empty prefs",
                    path, e);
        }
    }

    public static Set<Integer> getLockedSlots() {
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) return Collections.emptySet();
        Set<Integer> set = PER_WORLD.get(worldId);
        return set != null ? set : Collections.emptySet();
    }

    public static boolean isLocked(int containerSlotIndex) {
        return getLockedSlots().contains(containerSlotIndex);
    }

    /**
     * True if the given Slot is a lockable player slot (any of hotbar /
     * main / armor / offhand).
     *
     * <h3>Why UUID equality and not reference equality</h3>
     *
     * In single-player, the integrated server runs in the same JVM as
     * the client. Vanilla {@link
     * net.minecraft.world.inventory.AbstractContainerMenu#moveItemStackTo}
     * is invoked on BOTH threads (client predicts, server authoritative).
     * On the server thread, {@code slot.container} is {@code
     * ServerPlayer.getInventory()}; on the client thread it's {@code
     * LocalPlayer.getInventory()}. Those are different Java objects for
     * the same logical player, so reference equality {@code slot.container
     * == mc.player.getInventory()} returns false on the server thread,
     * making any mixin gated on this check a no-op server-side → client
     * predicts "blocked" but server places → revert → flicker.
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
     * True if the given Slot is in the "inventory + hotbar" subset
     * (container-slot 0-35) — the part that gets the edit-mode gray
     * overlay AND the edit-mode click-to-toggle.
     *
     * <p>Per Trev 2026-05-16: "Make it so you only can't interact with
     * the inventory and hotbar slots." Armor / offhand stay
     * vanilla-interactable in edit mode; their locks are toggled via the
     * {@code L} keybind only.
     *
     * <p>Uses UUID equality for the same reason as {@link #isLockable} —
     * the render-thread mixin needs the check to be stable on both client
     * and integrated-server threads.
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
     * The single lock-state predicate every enforcement path calls
     * ({@code moveItemStackTo} wrap, the shift-click mixin, Sorting, Move
     * Matching, the render mixin). Dispatches by slot kind:
     *
     * <ul>
     *   <li>player-owned slot → IP client store (player namespace);</li>
     *   <li>ender chest slot → IP client store (ender namespace);</li>
     *   <li>anything else (a placed container slot) → the first downstream
     *       provider that {@link SlotLockProvider#handles} it, if any.</li>
     * </ul>
     *
     * <p>Returns false for unlockable slots and for container slots no provider
     * owns — so with IPP absent, container slots are simply never locked, and
     * every existing rule keeps working unchanged on player + ender slots.
     */
    public static boolean isLockedSlot(Slot slot) {
        if (isLockable(slot)) {
            int cs = slot.getContainerSlot();
            return isLocked(cs) || isDerivedLocked(cs);
        }
        if (isEnderSlot(slot)) return isEnderLocked(slot.getContainerSlot());
        if (isCreatedSlot(slot)) return isCreatedLocked(slot);
        // IP's own client-side container locks come first: a placed container
        // IP can identify is IP's, per player, and any downstream provider is
        // only consulted for containers IP cannot name (Trev 2026-09-07).
        if (isPlacedContainerSlot(slot)) return isContainerLocked(slot);
        // Placed-container locks: the provider drives client prediction + the
        // feature skip-paths (sort / move-matching) + the lock icon, all on the
        // render thread. We deliberately do NOT consult it on the integrated-
        // server thread — server-side container enforcement is the companion's
        // mixin alone, because that's where the non-modded-player bypass
        // (capability gate) is applied. Consulting it here too would double-
        // block and would ignore the bypass.
        if (!isRenderThread()) return false;
        SlotLockProvider p = providerFor(slot);
        return p != null && p.isLocked(slot);
    }

    /** True on the client render thread (where the open-screen UI + prediction run). */
    private static boolean isRenderThread() {
        return "Render thread".equals(Thread.currentThread().getName());
    }

    /**
     * True if {@code slot} can be locked at all, in any namespace — used by the
     * {@code L} keybind (and its drag) to decide which slots respond. Includes
     * armor / offhand, which are lockable via {@code L} only.
     */
    public static boolean isLockableHere(Slot slot) {
        return isLockable(slot) || isEnderSlot(slot) || isCreatedSlot(slot)
                || isPlacedContainerSlot(slot) || providerFor(slot) != null;
    }

    /**
     * True if {@code slot} responds to lock-edit-mode click/drag toggling and
     * gets the gray overlay. This is exactly {@link #isLockableHere}: every
     * slot that can carry a lock can be edited in edit mode.
     *
     * <p>Until 2026-09-07 this was {@code isLockableHere} <i>minus</i> armor and
     * offhand (Trev 2026-05-16: only inventory and hotbar greyed and
     * click-toggled; armor and offhand stayed vanilla-interactable and were
     * locked by {@code L} alone). That carve-out collided with the newer rule
     * that created slots behave like vanilla slots: an Inventory Max elytra
     * slot greyed while the vanilla chestplate slot beside it did not. Trev
     * resolved it the other way: the vanilla outline slots should grey too.
     * So the carve-out goes and the two predicates are one.
     */
    public static boolean isEditModeToggleable(Slot slot) {
        return isLockableHere(slot);
    }

    /** Toggles {@code slot}'s lock, routing to the right namespace or provider. */
    public static void toggleSlot(Slot slot) {
        if (isLockable(slot)) {
            toggleByContainerSlot(slot.getContainerSlot());
        } else if (isEnderSlot(slot)) {
            toggleEnder(slot.getContainerSlot());
        } else if (isCreatedSlot(slot)) {
            toggleCreated(slot);
        } else if (isPlacedContainerSlot(slot)) {
            toggleContainer(slot);
        } else {
            SlotLockProvider p = providerFor(slot);
            if (p != null) p.setLocked(slot, !p.isLocked(slot));
        }
    }

    /** Coerces {@code slot} to {@code locked}, routing to the right namespace or provider. */
    public static void setLockedSlot(Slot slot, boolean locked) {
        if (isLockable(slot)) {
            setLocked(slot.getContainerSlot(), locked);
        } else if (isEnderSlot(slot)) {
            setEnderLocked(slot.getContainerSlot(), locked);
        } else if (isCreatedSlot(slot)) {
            setCreatedLocked(slot, locked);
        } else if (isPlacedContainerSlot(slot)) {
            setContainerLocked(slot, locked);
        } else {
            SlotLockProvider p = providerFor(slot);
            if (p != null) p.setLocked(slot, locked);
        }
    }

    // ── Created slots (MenuKit: Containers; client-side, own namespace) ────

    /**
     * True if {@code slot} is a MenuKit: Containers created slot (a pocket, an
     * equipment slot). Render-thread-gated like the ender check: the identity
     * comes from MenuKit's client addressing, and the server thread has no
     * business consulting a client lock store.
     */
    public static boolean isCreatedSlot(Slot slot) {
        if (!isRenderThread()) return false;
        if (slot.container instanceof Inventory) return false;   // a player slot
        return CreatedSlotKey.of(slot) != null;
    }

    private static Set<String> createdSet(boolean create) {
        String w = WorldIdentity.current(Minecraft.getInstance());
        if (w == null) return create ? new HashSet<>() : Set.of();
        return create ? PER_WORLD_CREATED.computeIfAbsent(w, k -> new HashSet<>()) : PER_WORLD_CREATED.getOrDefault(w, Set.of());
    }

    public static boolean isCreatedLocked(Slot slot) {
        String key = CreatedSlotKey.of(slot);
        return key != null && createdSet(false).contains(key);
    }

    public static void setCreatedLocked(Slot slot, boolean locked) {
        String key = CreatedSlotKey.of(slot);
        if (key == null) return;
        boolean changed = locked ? createdSet(true).add(key) : createdSet(true).remove(key);
        if (changed) save();
    }

    public static void toggleCreated(Slot slot) {
        setCreatedLocked(slot, !isCreatedLocked(slot));
    }

    // ── Placed-container slots (client-side, per-player, own namespace) ─────

    /**
     * The persistent key of the container the open menu shows, cached per
     * containerId. Null when nothing identifiable is open. First resolution
     * for a menu also prunes indices the container cannot hold (it shrank, or
     * a different block now stands there).
     */
    private static @Nullable String currentContainerKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) return null;
        if (menu.containerId != cachedContainerId) {
            cachedContainerId = menu.containerId;
            ContainerIdentity id = ContainerIdentity.forMenu(menu);
            cachedContainerKey = (id != null && id.isPersistent()) ? id.key() : null;
            if (cachedContainerKey != null) pruneToCapacity(cachedContainerKey, menu);
        }
        return cachedContainerKey;
    }

    /** Drop locked indices at or past the open container's size; drop the key if that empties it. */
    private static void pruneToCapacity(String key, AbstractContainerMenu menu) {
        int size = -1;
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) { size = s.container.getContainerSize(); break; }
        }
        if (size < 0) return;
        final int cap = size;   // effectively final for the lambda
        Map<String, Set<Integer>> world = containerMap(false);
        Set<Integer> set = world.get(key);
        if (set == null) return;
        int before = set.size();
        set.removeIf(i -> i >= cap);
        if (set.isEmpty()) world.remove(key);
        if (set.size() != before) {
            InventoryPlusClient.LOGGER.info("[locked-slots] pruned {} container lock(s) at {} (capacity {})", before - set.size(), key, size);
            save();
        }
    }

    /** Forget every lock on the container at {@code pos}. Called when the local player breaks it. */
    public static void pruneContainerAt(String key) {
        Map<String, Set<Integer>> world = containerMap(false);
        if (world.remove(key) != null) {
            InventoryPlusClient.LOGGER.info("[locked-slots] container broken, dropped its locks: {}", key);
            if (key.equals(cachedContainerKey)) cachedContainerKey = null;
            save();
        }
    }

    private static Map<String, Set<Integer>> containerMap(boolean create) {
        String w = WorldIdentity.current(Minecraft.getInstance());
        if (w == null) return create ? new HashMap<>() : Map.of();
        return create ? PER_WORLD_CONTAINERS.computeIfAbsent(w, k -> new HashMap<>()) : PER_WORLD_CONTAINERS.getOrDefault(w, Map.of());
    }

    /**
     * True if {@code slot} belongs to an identifiable placed container on the
     * open screen. Render-thread-gated like ender: identity comes from the
     * client's open-click tracker. Excludes player slots (a real
     * {@link Inventory}) and the ender chest, which have their own namespaces.
     */
    public static boolean isPlacedContainerSlot(Slot slot) {
        if (!isRenderThread()) return false;
        if (slot.container instanceof Inventory) return false;
        if (isEnderSlot(slot)) return false;
        return currentContainerKey() != null;
    }

    public static boolean isContainerLocked(Slot slot) {
        String key = currentContainerKey();
        if (key == null) return false;
        Set<Integer> set = containerMap(false).get(key);
        return set != null && set.contains(slot.getContainerSlot());
    }

    public static void setContainerLocked(Slot slot, boolean locked) {
        String key = currentContainerKey();
        if (key == null) return;
        Map<String, Set<Integer>> world = containerMap(true);
        Set<Integer> set = world.computeIfAbsent(key, k -> new HashSet<>());
        boolean changed = locked ? set.add(slot.getContainerSlot()) : set.remove(slot.getContainerSlot());
        if (set.isEmpty()) world.remove(key);
        if (changed) save();
    }

    public static void toggleContainer(Slot slot) {
        setContainerLocked(slot, !isContainerLocked(slot));
    }

    // ── Ender chest slots (client-side, per-player, own namespace) ──────────

    /**
     * True if {@code slot} is one of the local player's ender chest slots.
     * Ender is inherently single-viewer — you can only ever open your own — so
     * no local-player UUID check is needed: any {@link PlayerEnderChestContainer}
     * on screen is yours. (Contrast {@link #isLockable}, which must guard against
     * a placed container that merely looks like player inventory.)
     */
    public static boolean isEnderSlot(Slot slot) {
        // Server / SP integrated-server: the real ender container.
        if (slot.container instanceof PlayerEnderChestContainer) {
            int ci = slot.getContainerSlot();
            return ci >= 0 && ci < ENDER_SLOT_COUNT;
        }
        // Client: the open ender menu wraps a generic SimpleContainer (same as a
        // placed chest), so we identify the ender chest by the block the player
        // opened. Render-thread-gated: the integrated-server thread sees the real
        // PlayerEnderChestContainer above and must not consult client open-screen
        // state.
        if (!isRenderThread()) return false;
        if (slot.container instanceof Inventory) return false;   // a player slot
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci >= ENDER_SLOT_COUNT) return false;
        return ContainerOpenTracker.openContainerBlock() instanceof EnderChestBlock;
    }

    /** True if the given ender slot index is locked in the current world. */
    public static boolean isEnderLocked(int enderSlotIndex) {
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) return false;
        Set<Integer> set = PER_WORLD_ENDER.get(worldId);
        return set != null && set.contains(enderSlotIndex);
    }

    /** Flips the lock on an ender slot in the current world and persists. */
    public static void toggleEnder(int enderSlotIndex) {
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[locked-slots] ender toggle with no world id — not persisting");
            return;
        }
        Set<Integer> set = PER_WORLD_ENDER.computeIfAbsent(worldId, k -> new HashSet<>());
        // remove() returns false if it wasn't present → then add it.
        if (!set.remove(enderSlotIndex)) set.add(enderSlotIndex);
        save();
    }

    /** Coerces an ender slot to the given lock state (used by the drag controller). */
    public static void setEnderLocked(int enderSlotIndex, boolean locked) {
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[locked-slots] ender setLocked with no world id — not persisting");
            return;
        }
        Set<Integer> set = PER_WORLD_ENDER.computeIfAbsent(worldId, k -> new HashSet<>());
        boolean changed = locked ? set.add(enderSlotIndex) : set.remove(enderSlotIndex);
        if (changed) save();
    }

    public static void toggle(Slot slot) {
        if (!isLockable(slot)) return;
        toggleByContainerSlot(slot.getContainerSlot());
    }

    public static void toggleByContainerSlot(int containerSlotIndex) {
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[locked-slots] toggle with no world id — not persisting");
            return;
        }
        Set<Integer> set = PER_WORLD.computeIfAbsent(worldId, k -> new HashSet<>());
        boolean wasLocked = set.contains(containerSlotIndex);
        if (wasLocked) {
            set.remove(containerSlotIndex);
        } else {
            set.add(containerSlotIndex);
        }
        save();
    }

    /**
     * Coerces a slot to the given lock state. Used by the drag controller
     * to set every dragged slot to the target state (matching the first
     * slot's NEW state) without flipping slots that are already correct.
     * No-op if the slot is already at the target state.
     */
    public static void setLocked(int containerSlotIndex, boolean locked) {
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[locked-slots] setLocked with no world id — not persisting");
            return;
        }
        Set<Integer> set = PER_WORLD.computeIfAbsent(worldId, k -> new HashSet<>());
        boolean changed = locked ? set.add(containerSlotIndex) : set.remove(containerSlotIndex);
        if (changed) save();
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            JsonObject perWorld = new JsonObject();
            for (var entry : PER_WORLD.entrySet()) {
                JsonArray arr = new JsonArray();
                for (Integer i : entry.getValue()) arr.add(i);
                perWorld.add(entry.getKey(), arr);
            }
            root.add("perWorld", perWorld);
            JsonObject enderPerWorld = new JsonObject();
            for (var entry : PER_WORLD_ENDER.entrySet()) {
                JsonArray arr = new JsonArray();
                for (Integer i : entry.getValue()) arr.add(i);
                enderPerWorld.add(entry.getKey(), arr);
            }
            root.add("enderPerWorld", enderPerWorld);
            JsonObject createdPerWorld = new JsonObject();
            for (var entry : PER_WORLD_CREATED.entrySet()) {
                JsonArray arr = new JsonArray();
                for (String key : entry.getValue()) arr.add(key);
                createdPerWorld.add(entry.getKey(), arr);
            }
            root.add("createdPerWorld", createdPerWorld);
            JsonObject containersPerWorld = new JsonObject();
            for (var world : PER_WORLD_CONTAINERS.entrySet()) {
                JsonObject byKey = new JsonObject();
                for (var c : world.getValue().entrySet()) {
                    JsonArray arr = new JsonArray();
                    for (Integer i : c.getValue()) arr.add(i);
                    byKey.add(c.getKey(), arr);
                }
                containersPerWorld.add(world.getKey(), byKey);
            }
            root.add("containersPerWorld", containersPerWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-slots] failed to write {} — change won't survive a restart",
                    path, e);
        }
    }
}
