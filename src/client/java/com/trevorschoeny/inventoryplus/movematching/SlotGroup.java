package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * A "slot group" within an open container menu — a contiguous block of
 * slots sharing a {@link SlotRole} and backing {@link Container}.
 *
 * <h3>Targetability decision</h3>
 *
 * A group is "targetable" — meaning it counts toward the visibility
 * gate in {@link MoveMatchingButtons} — when {@link #targetable}
 * returns true:
 *
 * <ul>
 *   <li>{@link SlotRole#PLAYER_MAIN_INV} → always targetable. (The
 *       Move Matching widgets always render on the player main inv
 *       group when an external simplecontainer is present.)</li>
 *   <li>{@link SlotRole#EXTERNAL} → targetable iff the backing container
 *       is NOT in {@link #isNonTraditionalContainer}'s blacklist
 *       (crafting input / result are non-targetable).</li>
 *   <li>{@link SlotRole#PLAYER_HOTBAR} / {@link SlotRole#PLAYER_EQUIPMENT}
 *       → never targetable.</li>
 * </ul>
 *
 * <p>Post-2026-05-16 simplification, the widgets only render on the
 * player main inv group (and only when at least one EXTERNAL
 * targetable group is present alongside). The targetable flag on
 * EXTERNAL groups is now used purely as the "is this a real
 * simplecontainer?" gate.
 *
 * <h3>Bounds</h3>
 *
 * Slot positions are local to the screen's {@code leftPos}/{@code topPos}.
 * {@link #localTopY} / {@link #localRightX} recompute live each render.
 */
public record SlotGroup(
        List<Slot> slots,
        SlotRole role
) {

    public boolean targetable() {
        return switch (role) {
            case PLAYER_MAIN_INV -> true;
            case EXTERNAL -> {
                if (slots.isEmpty()) yield false;
                yield !isNonTraditionalContainer(slots.get(0).container);
            }
            case PLAYER_HOTBAR, PLAYER_EQUIPMENT -> false;
        };
    }

    /**
     * Vanilla "non-traditional" container types that may appear in
     * InventoryScreen alongside the player inventory: the crafting
     * result and the crafting input. Add new entries here as edge
     * cases surface.
     */
    public static boolean isNonTraditionalContainer(Container container) {
        return container instanceof ResultContainer
                || container instanceof CraftingContainer;
    }

    public int localTopY() {
        int top = Integer.MAX_VALUE;
        for (Slot slot : slots) if (slot.y < top) top = slot.y;
        return top == Integer.MAX_VALUE ? 0 : top;
    }

    public int localRightX() {
        int right = Integer.MIN_VALUE;
        for (Slot slot : slots) {
            int slotRight = slot.x + 16;
            if (slotRight > right) right = slotRight;
        }
        return right == Integer.MIN_VALUE ? 0 : right;
    }

    public boolean containsMenuIndex(int menuIndex) {
        for (Slot slot : slots) if (slot.index == menuIndex) return true;
        return false;
    }
}
