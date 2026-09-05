package com.trevorschoeny.inventoryplus.hotbarcycler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Row membership + rotation for Hotbar Cycler. Rotates whole inventory
 * rows through the hotbar; see
 * {@code mods/inventory-plus/plans/hotbar-cycler.md}.
 *
 * <h3>Rows, not slots</h3>
 *
 * Membership is per ROW, coarse on purpose (there is no per-slot
 * exclusion; Locked Slots covers the one-slot-must-not-move case). Rows
 * are numbered by their vanilla {@code Inventory.items[]} block, which
 * is also visual top → bottom:
 *
 * <ul>
 *   <li>row 0 — container slots 9-17, the TOP inventory row</li>
 *   <li>row 1 — container slots 18-26, the middle row</li>
 *   <li>row 2 — container slots 27-35, the row just above the hotbar</li>
 * </ul>
 *
 * The hotbar (0-8) is always in the cycle and is never a toggleable row.
 * Its button renders pressed and unclickable to say so.
 *
 * <h3>No cycle position</h3>
 *
 * There is deliberately no stored cycle index. The inventory IS the
 * position: whatever sits in the hotbar is what sits in the hotbar. That
 * is why a rotation needs no state migration when a row is toggled off
 * mid-session, and why toggling off leaves items exactly where they are.
 *
 * <h3>Rotation is nine column rotations</h3>
 *
 * Items stay in their column across a rotation (hotbar slot 1 goes to
 * row slot 1). So a row rotation is exactly Column Cycler's rotation run
 * over all nine columns, with row membership choosing the slots instead
 * of per-slot membership. {@link ColumnCyclerRotator#rotateSlots} is
 * that engine, and this class supplies the slot lists; there is no
 * second rotator.
 *
 * <h3>Persistence</h3>
 *
 * File: {@code config/inventoryplus/hotbar-cycler.json}, per-world and
 * client-side, mirroring {@code ColumnCycler} and {@code LockedSlots}.
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "perWorld": { "singleplayer:New World": { "rows": [1, 2] } }
 * }
 * }</pre>
 */
public final class HotbarCycler {

    private HotbarCycler() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Number of toggleable inventory rows above the hotbar. */
    public static final int ROW_COUNT = 3;
    /** Slots per row, and the number of columns a rotation spans. */
    public static final int COLUMNS = 9;

    /** {@code Map<worldId, Set<rowIndex>>}. */
    private static final Map<String, Set<Integer>> PER_WORLD = new HashMap<>();
    private static boolean loaded = false;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("hotbar-cycler.json");
    }

    // ── Row geometry ────────────────────────────────────────────────────

    /** First container slot of {@code row} (0-2). */
    public static int firstSlotOf(int row) {
        return 9 + row * COLUMNS;
    }

    /**
     * Row index (0-2) for a container slot, or -1 for the hotbar, armor,
     * offhand, or anything outside the main inventory.
     */
    public static int rowOf(int containerSlot) {
        if (containerSlot < 9 || containerSlot > 35) return -1;
        return (containerSlot - 9) / COLUMNS;
    }

    // ── Membership ──────────────────────────────────────────────────────

    /** Toggled rows for the current world, ascending. Never null. */
    public static Set<Integer> toggledRows() {
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) return Collections.emptySet();
        Set<Integer> rows = PER_WORLD.get(worldId);
        return rows == null ? Collections.emptySet() : Collections.unmodifiableSet(rows);
    }

    /** True if {@code row} (0-2) is in the cycle. */
    public static boolean isRowToggled(int row) {
        if (row < 0 || row >= ROW_COUNT) return false;
        return toggledRows().contains(row);
    }

    /**
     * True if {@code containerSlot} sits in a toggled row. The hotbar is
     * excluded here on purpose: it is always in the cycle, but it is not
     * a toggled row, and the two cases differ for locking (see
     * {@link #isCycledSlot}).
     */
    public static boolean isInToggledRow(int containerSlot) {
        int row = rowOf(containerSlot);
        return row >= 0 && isRowToggled(row);
    }

    /**
     * True if {@code containerSlot} is carried by an active row cycle:
     * any slot of a toggled row, plus the whole hotbar once at least one
     * row is on. This is the set the lock config governs.
     */
    public static boolean isCycledSlot(int containerSlot) {
        if (toggledRows().isEmpty()) return false;
        if (containerSlot >= 0 && containerSlot < COLUMNS) return true; // hotbar rides every cycle
        return isInToggledRow(containerSlot);
    }

    /** True when at least one row is toggled, i.e. the keybinds do something. */
    public static boolean hasActiveCycle() {
        return !toggledRows().isEmpty();
    }

    // ── Lock pairing ────────────────────────────────────────────────────

    /**
     * Whether a cycling row currently keeps automation off
     * {@code containerSlot}. Registered with
     * {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlots#registerDerivedPlayerLock}
     * so Sort, Move Matching, shift-click and the lock icon all honour it
     * through the one enforcement predicate they already call.
     *
     * <p>Two configs must both be on, per the spec: the global <b>Lock
     * Cycle Slots</b> that covers all three cyclers, and the
     * Hotbar-specific <b>Lock Cycled Rows</b>. That lets a player keep
     * column and pocket locking while leaving rows free to sort.
     *
     * <p>Derived, never stored. The lock belongs to the position, so it
     * stays put while items rotate through it, it appears and vanishes
     * the moment a row or a config is toggled, and it leaves the
     * player's own manual locks completely alone. A slot hand-locked
     * inside a toggled row still rotates: the lock keeps automation off
     * the position, it does not pin the item.
     */
    public static boolean rowLockApplies(int containerSlot) {
        if (!IPConfig.hotbarCyclerEnabled()) return false;
        if (!IPConfig.cycleSlotsLocked() || !IPConfig.lockCycledRows()) return false;
        return isCycledSlot(containerSlot);
    }

    /** Toggle {@code row} (0-2) into or out of the cycle and persist. */
    public static void toggleRow(int row) {
        setRow(row, !isRowToggled(row));
    }

    /** Set {@code row}'s membership and persist. No-op if already there. */
    public static void setRow(int row, boolean on) {
        if (row < 0 || row >= ROW_COUNT) return;
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) return;
        Set<Integer> rows = PER_WORLD.computeIfAbsent(worldId, k -> new LinkedHashSet<>());
        boolean changed = on ? rows.add(row) : rows.remove(row);
        if (!changed) return;
        if (rows.isEmpty()) PER_WORLD.remove(worldId);
        save();
        InventoryPlusClient.LOGGER.debug("[hotbar-cycler] row {} {}", row, on ? "on" : "off");
    }

    // ── Rotation ────────────────────────────────────────────────────────

    /**
     * Rotate every toggled row one step. FORWARD shifts rows down toward
     * the hotbar and wraps the hotbar's items up to the topmost toggled
     * row; BACKWARD reverses.
     *
     * <p>Runs as nine independent column rotations, which is what keeps
     * every item in its own column. Each column's chain leaves the cursor
     * empty, so the nine compose safely.
     *
     * <p>No-op with no toggled rows. With exactly one, the cycle is a
     * plain swap between that row and the hotbar, which falls out of the
     * same code with a two-element list.
     */
    public static boolean rotate(ColumnCyclerRotator.Direction direction) {
        if (!IPConfig.hotbarCyclerEnabled()) return false;
        List<Integer> rows = new ArrayList<>(toggledRows());
        if (rows.isEmpty()) return false;
        Collections.sort(rows); // visual top → bottom, so the wrap lands on the topmost

        boolean any = false;
        for (int column = 0; column < COLUMNS; column++) {
            // Cycle list for this column: each toggled row's slot in the
            // column, top → bottom, then the hotbar slot last. Same shape
            // ColumnCyclerRotator expects, so the engine is shared.
            List<Integer> slots = new ArrayList<>(rows.size() + 1);
            for (int row : rows) slots.add(firstSlotOf(row) + column);
            slots.add(column);
            any |= ColumnCyclerRotator.rotateSlots(slots, direction, "hotbar-cycler");
        }
        if (any) {
            for (RotationListener listener : ROTATION_LISTENERS) {
                listener.onRotation(direction);
            }
        }
        return any;
    }

    /**
     * Fired after a row rotation actually moved items. Separate from
     * {@link ColumnCyclerRotator.RotationListener} because that one is
     * per-column and a row rotation is not a column cycle advance; the
     * hotbar slide and the preview slides listen here.
     */
    @FunctionalInterface
    public interface RotationListener {
        void onRotation(ColumnCyclerRotator.Direction direction);
    }

    private static final List<RotationListener> ROTATION_LISTENERS = new ArrayList<>();

    /** Register a rotation listener. Idempotent for the same instance. */
    public static void addRotationListener(RotationListener listener) {
        if (listener == null || ROTATION_LISTENERS.contains(listener)) return;
        ROTATION_LISTENERS.add(listener);
    }

    // ── Persistence ─────────────────────────────────────────────────────

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject perWorld = root.has("perWorld")
                    ? root.getAsJsonObject("perWorld")
                    : new JsonObject();
            int total = 0;
            for (var entry : perWorld.entrySet()) {
                JsonObject world = entry.getValue().getAsJsonObject();
                if (!world.has("rows")) continue;
                JsonArray arr = world.getAsJsonArray("rows");
                Set<Integer> rows = new LinkedHashSet<>();
                for (var el : arr) {
                    int row = el.getAsInt();
                    if (row >= 0 && row < ROW_COUNT) rows.add(row);
                }
                if (!rows.isEmpty()) {
                    PER_WORLD.put(entry.getKey(), rows);
                    total += rows.size();
                }
            }
            InventoryPlusClient.LOGGER.info(
                    "[hotbar-cycler] loaded {} row(s) across {} world(s) from {}",
                    total, PER_WORLD.size(), path);
        } catch (IOException | JsonSyntaxException | IllegalStateException
                 | NumberFormatException | UnsupportedOperationException e) {
            InventoryPlusClient.LOGGER.error(
                    "[hotbar-cycler] failed to read {} — starting empty", path, e);
        }
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            JsonObject perWorld = new JsonObject();
            for (var entry : PER_WORLD.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                JsonArray arr = new JsonArray();
                entry.getValue().stream().sorted().forEach(arr::add);
                JsonObject world = new JsonObject();
                world.add("rows", arr);
                perWorld.add(entry.getKey(), world);
            }
            root.add("perWorld", perWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[hotbar-cycler] failed to write {} — change won't survive a restart", path, e);
        }
    }
}
