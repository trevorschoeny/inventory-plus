package com.trevorschoeny.inventoryplus.lockedslots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
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

    // ── Downstream lock providers (the SlotLockProvider seam) ───────────────
    //
    // IP locks its own client-side slots (player + ender) directly. Placed-
    // container locks are shared and live in MenuKit's server-authoritative
    // channel, which IP (client-only) can't reach — so IPP registers a provider
    // here at its init and the unified predicates below dispatch container slots
    // to it. IP itself registers nothing.
    private static final List<SlotLockProvider> PROVIDERS = new ArrayList<>();

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
            InventoryPlusClient.LOGGER.info(
                    "[locked-slots] loaded {} player + {} ender entries across {} world(s) from {}",
                    total, enderTotal, PER_WORLD.size(), path);
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
        if (isLockable(slot)) return isLocked(slot.getContainerSlot());
        if (isEnderSlot(slot)) return isEnderLocked(slot.getContainerSlot());
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
        return isLockable(slot) || isEnderSlot(slot) || providerFor(slot) != null;
    }

    /**
     * True if {@code slot} responds to lock-edit-mode click/drag toggling and
     * gets the gray overlay. This is {@link #isLockableHere} <i>minus</i> armor
     * and offhand: edit-mode click was deliberately narrowed to the inv+hotbar
     * subset (Trev 2026-05-16, so armor/offhand stay vanilla-interactable in
     * edit mode), and ender + placed-container slots — which have no such
     * carve-out — join that toggleable set.
     */
    public static boolean isEditModeToggleable(Slot slot) {
        return isInvOrHotbarSlot(slot) || isEnderSlot(slot) || providerFor(slot) != null;
    }

    /** Toggles {@code slot}'s lock, routing to the right namespace or provider. */
    public static void toggleSlot(Slot slot) {
        if (isLockable(slot)) {
            toggleByContainerSlot(slot.getContainerSlot());
        } else if (isEnderSlot(slot)) {
            toggleEnder(slot.getContainerSlot());
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
        } else {
            SlotLockProvider p = providerFor(slot);
            if (p != null) p.setLocked(slot, locked);
        }
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
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-slots] failed to write {} — change won't survive a restart",
                    path, e);
        }
    }
}
