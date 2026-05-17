package com.trevorschoeny.inventoryplus.lockedslots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

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

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("locked-slots.json");
    }

    private static final Map<String, Set<Integer>> PER_WORLD = new HashMap<>();

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
            InventoryPlusClient.LOGGER.info(
                    "[locked-slots] loaded {} entries across {} world(s) from {}",
                    total, PER_WORLD.size(), path);
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

    /** True if the given Slot is a lockable player slot (any of hotbar / main / armor / offhand). */
    public static boolean isLockable(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Inventory playerInv = mc.player.getInventory();
        if (slot.container != playerInv) return false;
        int ci = slot.getContainerSlot();
        return ci >= 0 && ci <= MAX_PLAYER_CONTAINER_SLOT;
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
     */
    public static boolean isInvOrHotbarSlot(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Inventory playerInv = mc.player.getInventory();
        if (slot.container != playerInv) return false;
        int ci = slot.getContainerSlot();
        return ci >= 0 && ci <= MAX_INV_HOTBAR_CONTAINER_SLOT;
    }

    public static boolean isLockedSlot(Slot slot) {
        if (!isLockable(slot)) return false;
        return isLocked(slot.getContainerSlot());
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
            LockedSlotsCorrector.onLockChanged(containerSlotIndex, false);
        } else {
            set.add(containerSlotIndex);
            LockedSlotsCorrector.onLockChanged(containerSlotIndex, true);
        }
        save();
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
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-slots] failed to write {} — change won't survive a restart",
                    path, e);
        }
    }
}
