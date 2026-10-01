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
 * the lock stores (per-world, JSON-backed).
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
 * value for hotbar slots.
 *
 * <p>Armor / offhand can't be cycled at all — they're equipment, not
 * inventory positions.
 *
 * <h3>Reach group (Trev 2026-09-30)</h3>
 *
 * The cycle slots and the hotbar slot of every active column are Column
 * Cycler's feature reach group ({@link #inReachGroup}, `plans/reach.md`):
 * every move Slot lock stops by default leaves them alone, which the
 * player changes per move in Reach. They carry no lock; {@code L} puts a
 * real Slot lock on top, and the cycle mark is what they show. Until
 * 2026-09-30 this was the "Lock the slots the cyclers use" pairing, a
 * derived Slot lock behind one global switch.
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
        CYCLE_SLOTS.modify(slots -> WorldStore.withElement(slots, slot, true));
    }

    private static void removeCycleInternal(int slot) {
        CYCLE_SLOTS.modify(slots -> WorldStore.withElement(slots, slot, false));
    }

    /**
     * The player slots the pre-Reach pairing wrote Slot locks onto in
     * {@code world}: its cycle slots and the hotbar slot of every column that
     * has one. The 1.5.x lock migration drops those stored locks, since the
     * derived lock now covers them (LockedSlots.load). Needs this class's
     * file loaded first.
     */
    public static Set<Integer> pairedSlotsIn(String world) {
        Set<Integer> out = new HashSet<>();
        CYCLE_SLOTS.forEachWorld((w, slots) -> {
            if (!w.equals(world)) return;
            for (int slot : slots) {
                out.add(slot);
                out.add(slot % 9);
            }
        });
        return out;
    }

    /**
     * Whether player slot {@code containerSlotIndex} is in Column Cycler's
     * reach group now: a cycle slot, or the hotbar slot below an active
     * column. Registered with {@code Reach.registerFeature}.
     */
    public static boolean inReachGroup(int containerSlotIndex) {
        return isCycleSlot(containerSlotIndex);
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
