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
 * <h3>Targetability decision (Trev 2026-05-16, container-blacklist edition)</h3>
 *
 * A group "hosts a move-matching button" when {@link #targetable} returns
 * true. The decision is data-driven from the group's role + container
 * type — no screen-class check:
 *
 * <ul>
 *   <li>{@link SlotRole#PLAYER_MAIN_INV} → always targetable.</li>
 *   <li>{@link SlotRole#EXTERNAL} → targetable iff the backing container
 *       is NOT in {@link #isNonTraditionalContainer}'s blacklist.</li>
 *   <li>{@link SlotRole#PLAYER_HOTBAR} / {@link SlotRole#PLAYER_EQUIPMENT}
 *       → never targetable.</li>
 * </ul>
 *
 * <p>The blacklist covers vanilla's known "non-traditional" containers
 * that share the InventoryScreen (crafting result, crafting input). Any
 * other EXTERNAL container — chest, shulker, hopper, dispenser, and any
 * future feature that surfaces a real container (e.g., Trev's hypothetical
 * Shulker Peek panel inside the InventoryScreen) — is treated as
 * traditional and gets a button.
 *
 * <p>The 2+ rule lives in {@link MoveMatchingButtons}: even if all groups
 * are independently targetable, buttons are only registered when at
 * least two are present on the same screen. So a standalone vanilla
 * InventoryScreen yields one targetable group (main inv) → no buttons.
 * Open a chest, or open a future Shulker Peek inside the inventory →
 * two targetable groups → buttons appear automatically.
 *
 * <h3>Bounds</h3>
 *
 * Slot positions are local to the screen's {@code leftPos}/{@code topPos}.
 * {@link #localTopY} / {@link #localRightX} recompute live each render.
 */
public record SlotGroup(
        ContainerKey key,
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
     * cases surface — the blacklist is the load-bearing line for keeping
     * the player's UI buttons honest.
     *
     * <p>Note: {@link CraftingContainer} is the interface;
     * {@link net.minecraft.world.inventory.TransientCraftingContainer}
     * is the concrete impl used in {@link net.minecraft.world.inventory.InventoryMenu}.
     * We check the interface to catch any other CraftingContainer impls.
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
