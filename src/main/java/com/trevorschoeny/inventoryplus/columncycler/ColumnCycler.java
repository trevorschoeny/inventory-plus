package com.trevorschoeny.inventoryplus.columncycler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.api.WorldStore;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Central state + persistence for Column Cycler — mirrors the shape of
 * {@link LockedSlots} (per-world Set of slot indices, JSON-backed).
 *
 * <h3>Scope</h3>
 *
 * <b>Direct cycle membership</b> applies to INV slots only (container-slot
 * 9-35). {@link #isCycleable(Slot)} enforces this — {@code C} can't toggle
 * hotbar slots directly, because a cycle with only a hotbar slot has
 * nothing to cycle to.
 *
 * <p><b>Derived cycle membership</b> applies to hotbar slots (0-8): a
 * hotbar slot is "cycle-active" iff any inv slot in its column is in
 * the stored cycle set. {@link #isCycleSlot(int)} returns the derived
 * value for hotbar slots. The hotbar slot's lock state follows its
 * column under {@link IPConfig#cycleSlotsLocked}.
 *
 * <p>Armor / offhand can't be cycled at all — they're equipment, not
 * inventory positions.
 *
 * <h3>Lock pairing (Trev 2026-05-19)</h3>
 *
 * Under {@link IPConfig#cycleSlotsLocked} ON (default), cycle and lock
 * are bound as one unit: adding an inv slot to a cycle locks it AND
 * its column's hotbar slot; removing the last cycle slot in a column
 * unlocks both. {@code L} is a no-op on cycle slots (direct or derived);
 * the lock icon is suppressed (the cycle icon represents both).
 *
 * <p>Under {@code cycleSlotsLocked} OFF, cycle and lock are fully
 * independent — toggling cycle does nothing to lock state, and {@code L}
 * works normally on cycle slots.
 *
 * <p>{@link #enforceCycleLockingInvariant} is called when the config
 * flips OFF → ON to retroactively lock any cycle slots (direct + derived
 * hotbar) that aren't.
 *
 * <h3>Persistence</h3>
 *
 * File: {@code config/inventoryplus/column-cycler.json}.
 * <pre>{@code
 * {
 *   "version": 1,
 *   "perWorld": {
 *     "singleplayer:New World": {
 *       "cycleSlots": [9, 10, 18]
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Per-world is correct for the same reason as Locked Slots — players
 * have different layouts per world.
 */
public final class ColumnCycler {

    private ColumnCycler() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Lower bound (inclusive) on directly-cycleable container-slot indices — inv only, no hotbar. */
    public static final int MIN_DIRECT_CYCLE_SLOT = 9;
    /** Upper bound (inclusive) on cycleable container-slot indices — main inv only, no armor/offhand. */
    public static final int MAX_CYCLEABLE_CONTAINER_SLOT = 35;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("column-cycler.json");
    }

    /**
     * Direct cycle slots (container slots 9-35) per world. A {@link WorldStore}:
     * immutable, ascending, saved only when a write changes it. Before 1.6.0
     * this was a hand-rolled map whose write accessor created an empty entry
     * for the world on every call, and whose values callers mutated in place.
     */
    private static final WorldStore<Set<Integer>> CYCLE_SLOTS = WorldStore.sortedSet(ColumnCycler::save);

    /**
     * Per-inv-open memory of "what was this slot's lock state BEFORE
     * cycle was first applied to it this session?" (Trev 2026-05-19).
     * Lets us restore the player's pre-cycle manual lock when they
     * remove the cycle — without persisting across game restarts.
     *
     * <p>For inv slots (9-35): recorded on first {@link #addCycleInternal}
     * this session. Restored on {@link #removeCycleInternal}.
     *
     * <p>For derived hotbar slots (0-8): recorded on the column-becomes-
     * active transition (when an inv add causes its column to flip from
     * inactive to active). Restored on the column-goes-inactive transition.
     *
     * <p>Cleared on every {@code ScreenEvents.AFTER_INIT} — opening any
     * new screen starts a fresh inv-open session. Across-session lock
     * loss remains accepted (the manual-lock-preservation guarantee
     * only applies within one continuous screen session).
     */
    private static final Map<Integer, Boolean> SESSION_PRE_LOCK = new HashMap<>();

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
            int worlds = 0;
            for (var worldEntry : perWorld.entrySet()) {
                String worldId = worldEntry.getKey();
                JsonObject worldObj = worldEntry.getValue().getAsJsonObject();
                Set<Integer> cycleSlots = new HashSet<>();
                if (worldObj.has("cycleSlots")) {
                    JsonArray arr = worldObj.getAsJsonArray("cycleSlots");
                    for (var v : arr) cycleSlots.add(v.getAsInt());
                }
                if (!cycleSlots.isEmpty()) {
                    CYCLE_SLOTS.load(worldId, cycleSlots);
                    worlds++;
                    total += cycleSlots.size();
                }
            }
            InventoryPlusClient.LOGGER.info(
                    "[column-cycler] loaded {} cycle slot(s) across {} world(s) from {}",
                    total, worlds, path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[column-cycler] failed to parse {} — starting with empty prefs",
                    path, e);
        }
    }

    public static Set<Integer> getCycleSlots() {
        return CYCLE_SLOTS.get();
    }

    /**
     * True if the given container-slot index is part of an active cycle.
     * Inv slots (9-35) return their direct membership; hotbar slots
     * (0-8) return the DERIVED value — true iff any inv slot in the
     * same column is in the stored cycle set.
     *
     * <p>Returns false when the feature is disabled
     * ({@code !IPConfig.columnCyclerEnabled()}) — the stored cycle data
     * stays on disk for when the feature is re-enabled, but no slot is
     * treated as a logical cycle member while disabled (icons hide,
     * keybinds no-op, lock suppression lifts).
     */
    public static boolean isCycleSlot(int containerSlotIndex) {
        if (!IPConfig.columnCyclerEnabled()) return false;
        if (containerSlotIndex < 0 || containerSlotIndex > MAX_CYCLEABLE_CONTAINER_SLOT) return false;
        Set<Integer> cycleSlots = CYCLE_SLOTS.get();
        if (containerSlotIndex >= MIN_DIRECT_CYCLE_SLOT) {
            return cycleSlots.contains(containerSlotIndex);
        }
        // Hotbar (0-8): derived from column. col == hotbar slot index.
        int col = containerSlotIndex;
        return cycleSlots.contains(9 + col)
                || cycleSlots.contains(18 + col)
                || cycleSlots.contains(27 + col);
    }

    public static boolean isCycleSlot(Slot slot) {
        if (!isLocalPlayerInvOrHotbar(slot)) return false;
        return isCycleSlot(slot.getContainerSlot());
    }

    /**
     * True if this slot can be DIRECTLY toggled by C — INV slots only
     * (9-35). Hotbar slots aren't directly cycleable (their cycle state
     * is derived from their column; a hotbar-only cycle has nothing to
     * cycle to). Armor / offhand are excluded entirely.
     */
    public static boolean isCycleable(Slot slot) {
        if (!isLocalPlayerInvOrHotbar(slot)) return false;
        int ci = slot.getContainerSlot();
        return ci >= MIN_DIRECT_CYCLE_SLOT && ci <= MAX_CYCLEABLE_CONTAINER_SLOT;
    }

    /**
     * UUID-stable check for "is this Slot one of the local player's
     * inv-or-hotbar (0-35) positions" — the underlying eligibility
     * filter shared by {@link #isCycleable} and {@link #isCycleSlot}.
     */
    private static boolean isLocalPlayerInvOrHotbar(Slot slot) {
        if (!(slot.container instanceof Inventory inv)) return false;
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci > MAX_CYCLEABLE_CONTAINER_SLOT) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        return inv.player.getUUID().equals(mc.player.getUUID());
    }

    public static void toggle(Slot slot) {
        if (!isCycleable(slot)) return;
        toggleByContainerSlot(slot.getContainerSlot());
    }

    public static void toggleByContainerSlot(int containerSlotIndex) {
        setCycle(containerSlotIndex, !CYCLE_SLOTS.get().contains(containerSlotIndex));
    }

    /**
     * Coerce cycle membership to the given state — used by the drag
     * controller to set every dragged slot to the target state without
     * flipping slots already at that state.
     */
    public static void setCycle(int containerSlotIndex, boolean cycle) {
        if (CYCLE_SLOTS.get().contains(containerSlotIndex) == cycle) return;
        if (cycle) {
            addCycleInternal(containerSlotIndex);
        } else {
            removeCycleInternal(containerSlotIndex);
        }
    }

    private static void addCycleInternal(int slot) {
        // Callers guarantee slot is in 9-35 (isCycleable). Compute the
        // column's current activeness BEFORE the add so we know if the
        // column is becoming active (transition false → true).
        int hotbarSlot = slot % 9; // 0-8
        boolean colWasActive = isCycleSlot(hotbarSlot);
        // Write the store FIRST: the lock side effects below read cycle state
        // back through isCycleSlot, and must see the slot as added.
        CYCLE_SLOTS.modify(slots -> WorldStore.withElement(slots, slot, true));
        if (IPConfig.cycleSlotsLocked()) {
            // Record the slot's pre-cycle lock state once per session,
            // BEFORE we change it. Used on remove to restore the
            // player's prior manual lock (or lack thereof).
            SESSION_PRE_LOCK.putIfAbsent(slot, LockedSlots.isLocked(slot));
            LockedSlots.setLocked(slot, true);
            // If the column just transitioned to active, lock the hotbar
            // slot too — it's now a derived cycle member. Also record
            // the hotbar slot's pre-transition lock state.
            if (!colWasActive) {
                SESSION_PRE_LOCK.putIfAbsent(hotbarSlot, LockedSlots.isLocked(hotbarSlot));
                LockedSlots.setLocked(hotbarSlot, true);
            }
        }
    }

    private static void removeCycleInternal(int slot) {
        // Write the store FIRST: the "did the column go inactive?" check below
        // reads isCycleSlot and must see the slot as already removed.
        CYCLE_SLOTS.modify(slots -> WorldStore.withElement(slots, slot, false));
        if (IPConfig.cycleSlotsLocked()) {
            // Restore the slot's pre-cycle lock state if we have it
            // (recorded this session); else unlock. Removing the entry
            // after use means a subsequent re-add this session will
            // re-record from the current state.
            Boolean prevLock = SESSION_PRE_LOCK.remove(slot);
            LockedSlots.setLocked(slot, prevLock != null ? prevLock : false);
            // If the column went inactive (no more inv cycle slots),
            // restore the hotbar slot's pre-transition lock state too.
            int hotbarSlot = slot % 9;
            if (!isCycleSlot(hotbarSlot)) {
                Boolean prevHotbarLock = SESSION_PRE_LOCK.remove(hotbarSlot);
                LockedSlots.setLocked(hotbarSlot, prevHotbarLock != null ? prevHotbarLock : false);
            }
        }
    }

    /**
     * Clears the per-inv-open memory of pre-cycle lock state. Called
     * on every {@code ScreenEvents.AFTER_INIT} so opening a new screen
     * starts a fresh inv-open session.
     */
    public static void clearSessionPreLockState() {
        SESSION_PRE_LOCK.clear();
    }

    /**
     * Changes "lock cycle slots". Turning it on re-locks the cycle slots that
     * were unlocked while it was off. Every settings screen changes the
     * setting through here, so the rule lives in one place.
     */
    public static void setCycleSlotsLocked(boolean on) {
        boolean wasOn = IPConfig.cycleSlotsLocked();
        IPConfig.setCycleSlotsLocked(on);
        if (on && !wasOn) enforceCycleLockingInvariant();
    }

    /**
     * Called when {@code cycleSlotsLocked} flips OFF → ON. Locks every
     * direct cycle slot (inv) plus every derived hotbar slot whose column
     * is active. Other worlds will be enforced the next time the player
     * enters them and toggles anything (their cycle slots get re-locked
     * on next ADD path).
     *
     * <p>No-op when the feature is disabled — there's no "active" cycle
     * state to enforce against; storage stays untouched until the player
     * re-enables.
     */
    public static void enforceCycleLockingInvariant() {
        if (!IPConfig.columnCyclerEnabled()) return;
        for (int slot : CYCLE_SLOTS.get()) {
            if (!LockedSlots.isLocked(slot)) {
                LockedSlots.setLocked(slot, true);
            }
        }
        // Derived hotbar slots — lock any hotbar whose column is active.
        for (int col = 0; col < 9; col++) {
            if (isCycleSlot(col) && !LockedSlots.isLocked(col)) {
                LockedSlots.setLocked(col, true);
            }
        }
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            JsonObject perWorld = new JsonObject();
            CYCLE_SLOTS.forEachWorld((worldId, cycleSlots) -> {
                JsonObject worldObj = new JsonObject();
                JsonArray cycleArr = new JsonArray();
                for (Integer i : cycleSlots) cycleArr.add(i);
                worldObj.add("cycleSlots", cycleArr);
                perWorld.add(worldId, worldObj);
            });
            root.add("perWorld", perWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[column-cycler] failed to write {} — change won't survive a restart",
                    path, e);
        }
    }
}
