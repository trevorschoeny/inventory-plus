package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * A "slot group" within an open container menu — a contiguous block of
 * slots that share a logical identity. Examples:
 *
 * <ul>
 *   <li>The chest's 27 (or 54) slots on a {@code ContainerScreen}.</li>
 *   <li>The player's main inventory 3×9 grid (slot-indices 9-35 within
 *       {@code Inventory.items}).</li>
 *   <li>The player's hotbar (slot-indices 0-8) — detected but never used
 *       as a target or source per spec §"Scope".</li>
 *   <li>A hopper's 5 slots / dispenser's 9 slots / etc.</li>
 * </ul>
 *
 * <h3>What this record holds</h3>
 *
 * <ul>
 *   <li>{@link #key} — the {@link ContainerKey} used to persist this
 *       group's cycle setting. May be {@code null} for groups whose key
 *       can't be resolved (minecart hoppers, etc.); those groups still
 *       function but read the global default cycle.</li>
 *   <li>{@link #slots} — the menu slots that make up this group. Read
 *       at execution time to compute the match-set (for target groups)
 *       or to skip when iterating sources (for the active target).</li>
 *   <li>{@link #targetable} — whether this group can be a target (host a
 *       button). Hotbar and "specialized slot" groups (crafting result,
 *       furnace fuel, brewing-stand bottle, etc.) are non-targetable —
 *       they can still serve as sources per Trev's permissive-source
 *       direction, but won't display a button.</li>
 * </ul>
 *
 * <h3>Bounds</h3>
 *
 * Slot positions ({@code Slot.x}, {@code Slot.y}) are relative to the
 * screen's {@code leftPos}/{@code topPos}. The slot group's bounds are
 * recomputed live each render — the {@link #localTopY}, {@link #localRightX}
 * helpers iterate {@link #slots} fresh because vanilla can technically
 * reposition slots on resize (rare in practice for container screens,
 * but cheap to honor).
 */
public record SlotGroup(
        ContainerKey key,
        List<Slot> slots,
        boolean targetable
) {

    /**
     * Local Y of the top of this slot group — the smallest
     * {@code slot.y} among its slots. Add screen.topPos for screen-space.
     */
    public int localTopY() {
        int top = Integer.MAX_VALUE;
        for (Slot slot : slots) {
            if (slot.y < top) top = slot.y;
        }
        return top == Integer.MAX_VALUE ? 0 : top;
    }

    /**
     * Local X of the right edge of this slot group — the rightmost
     * {@code slot.x + 16} among its slots. Add screen.leftPos for
     * screen-space.
     */
    public int localRightX() {
        int right = Integer.MIN_VALUE;
        for (Slot slot : slots) {
            int slotRight = slot.x + 16;
            if (slotRight > right) right = slotRight;
        }
        return right == Integer.MIN_VALUE ? 0 : right;
    }

    /** True if any of this group's slots contains the given menu-slot index. */
    public boolean containsMenuIndex(int menuIndex) {
        for (Slot slot : slots) {
            if (slot.index == menuIndex) return true;
        }
        return false;
    }
}
