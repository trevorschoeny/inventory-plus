package com.trevorschoeny.inventoryplus.features.autorestock;

import com.trevorschoeny.inventoryplus.InventoryPlus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Autorestock: when the player's currently selected hotbar slot drops to zero
 * (placed last block, ate last food, threw last ender pearl, etc.), pull more
 * of the same item from the main inventory into that slot.
 *
 * <p>This is called each server tick from {@code IPAutoRestockMixin}. We track
 * the previous item in the SELECTED hotbar slot so we can detect the transition
 * from "had items" to "now empty". When that happens, we search the main
 * inventory (slots 9-35) for a matching stack and swap it in.
 *
 * <p><b>Why only the selected slot?</b> Tracking all 9 hotbar slots causes
 * false-triggers when the player intentionally empties a non-selected slot
 * (Q-drop, manual move to main inventory). The selected slot is the one the
 * player is actively using — when it empties, it's almost certainly a depletion
 * event, not an intentional rearrangement.
 *
 * <p><b>Why match by Item type only (not components)?</b> The player wants
 * ANY stack of the same block/item to refill. If they place their last
 * cobblestone, they want cobblestone from inventory regardless of whether
 * one stack has a custom name or enchantment glint. Strict component matching
 * would break the intuitive "refill my stack" expectation.
 */
public final class AutoRestock {

    // Main inventory starts at slot 9 and goes to 35 (27 slots).
    // We search these for replacement stacks.
    private static final int MAIN_INV_START = 9;
    private static final int MAIN_INV_END = 35; // inclusive

    /**
     * Checks the selected hotbar slot for a "depleted" transition and restocks if possible.
     *
     * @param player       the server player to check
     * @param previousItem the Item that was in the selected slot last tick (null if empty)
     * @param selectedSlot the currently selected hotbar slot index (0-8)
     * @return the current Item in the selected slot (for tracking next tick), or null if empty
     */
    public static Item tick(ServerPlayer player, Item previousItem, int selectedSlot) {
        // Creative players have infinite items — restocking is meaningless
        if (player.isCreative()) return null;
        // Spectators have no inventory interaction
        if (player.isSpectator()) return null;

        Inventory inv = player.getInventory();
        ItemStack current = inv.getItem(selectedSlot);

        if (current.isEmpty() && previousItem != null) {
            // Selected slot transitioned from non-empty to empty — try to restock.
            // Search main inventory (slots 9-35) for a stack of the same Item type.
            int sourceSlot = findMatchingSlot(inv, previousItem);

            if (sourceSlot >= 0) {
                // Move the entire stack from the source slot into the hotbar slot.
                ItemStack sourceStack = inv.getItem(sourceSlot);
                inv.setItem(selectedSlot, sourceStack);
                inv.setItem(sourceSlot, ItemStack.EMPTY);

                current = inv.getItem(selectedSlot);

                InventoryPlus.LOGGER.debug(
                        "[AutoRestock] Restocked slot {} with {} from slot {}",
                        selectedSlot, sourceStack.getHoverName().getString(), sourceSlot);
            }
        }

        // Return what's in the selected slot now for next-tick tracking.
        return current.isEmpty() ? null : current.getItem();
    }

    /**
     * Searches the main inventory (slots 9-35) for a stack matching the given
     * Item type. Returns the slot index, or -1 if none found.
     *
     * <p>Prefers the first match (lowest slot index). This gives predictable
     * behavior: items restock from top-left of the inventory, which is where
     * players typically keep overflow stacks.
     */
    private static int findMatchingSlot(Inventory inv, Item targetItem) {
        for (int i = MAIN_INV_START; i <= MAIN_INV_END; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(targetItem)) {
                return i;
            }
        }
        return -1;
    }

    private AutoRestock() {} // Utility class — no instantiation
}
