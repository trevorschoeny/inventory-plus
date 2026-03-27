package com.trevorschoeny.inventoryplus.features.autorestock;

import com.trevorschoeny.inventoryplus.InventoryPlus;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;

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
            // Priority: loose inventory first, then shulker boxes, then bundles.
            // This ordering keeps the most accessible items flowing in first.

            // 1. Search main inventory (slots 9-35) for a loose stack
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

            // 2. No loose stack found — try extracting from shulker boxes
            if (sourceSlot < 0) {
                ItemStack extracted = extractFromShulker(inv, previousItem);
                if (!extracted.isEmpty()) {
                    inv.setItem(selectedSlot, extracted);
                    current = extracted;

                    InventoryPlus.LOGGER.debug(
                            "[AutoRestock] Restocked slot {} with {} from shulker box",
                            selectedSlot, extracted.getHoverName().getString());
                }
            }

            // 3. Still nothing — try extracting from bundles
            if (current.isEmpty()) {
                ItemStack extracted = extractFromBundle(inv, previousItem);
                if (!extracted.isEmpty()) {
                    inv.setItem(selectedSlot, extracted);
                    current = extracted;

                    InventoryPlus.LOGGER.debug(
                            "[AutoRestock] Restocked slot {} with {} from bundle",
                            selectedSlot, extracted.getHoverName().getString());
                }
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

    // ── Shulker Extraction ─────────────────────────────────────────────────

    /** Shulker boxes always have 27 slots. */
    private static final int SHULKER_SIZE = 27;

    /**
     * Extracts a full stack of the given item type from the first shulker box
     * in the player's inventory that contains it.
     *
     * <p>Uses the same read-modify-write pattern on {@link ItemContainerContents}
     * as {@code AutoFill.extractFromShulkers}. Matches by Item type only (not
     * components) — same rationale as {@link #findMatchingSlot}: the player
     * wants ANY stack of the same item to refill their hotbar.
     *
     * @param inv        the player's inventory
     * @param targetItem the item type to extract
     * @return the extracted stack, or {@link ItemStack#EMPTY} if none found
     */
    private static ItemStack extractFromShulker(Inventory inv, Item targetItem) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack containerStack = inv.getItem(i);
            if (!isShulkerBox(containerStack)) continue;

            ItemContainerContents contents = containerStack.get(DataComponents.CONTAINER);
            if (contents == null) continue;

            // Read shulker items into a mutable list
            NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
            contents.copyInto(items);

            // Find the first matching item in this shulker
            for (int j = 0; j < items.size(); j++) {
                ItemStack shulkerItem = items.get(j);
                if (shulkerItem.isEmpty() || !shulkerItem.is(targetItem)) continue;

                // Take the entire slot — move it directly to the hotbar
                ItemStack result = shulkerItem.copy();
                items.set(j, ItemStack.EMPTY);

                // Write back modified shulker contents
                containerStack.set(DataComponents.CONTAINER,
                        ItemContainerContents.fromItems(items));

                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    // ── Bundle Extraction ────────────────────────────────────────────────

    /**
     * Extracts items of the given type from the first bundle in the player's
     * inventory that contains them.
     *
     * <p>Bundles use {@link BundleContents} instead of {@link ItemContainerContents}.
     * We iterate the bundle's items, collect matching ones (up to max stack size),
     * and rebuild the BundleContents without the extracted items.
     *
     * @param inv        the player's inventory
     * @param targetItem the item type to extract
     * @return the extracted stack, or {@link ItemStack#EMPTY} if none found
     */
    private static ItemStack extractFromBundle(Inventory inv, Item targetItem) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack bundleStack = inv.getItem(i);
            BundleContents contents = bundleStack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents == null) continue;

            // Collect matching and non-matching items separately
            ItemStack result = ItemStack.EMPTY;
            List<ItemStack> remaining = new ArrayList<>();
            boolean found = false;

            for (ItemStack bundleItem : contents.items()) {
                if (bundleItem.isEmpty()) continue;

                if (!found && bundleItem.is(targetItem)) {
                    // Take this entire stack as the restock source
                    result = bundleItem.copy();
                    found = true;
                    // Don't add to remaining — it's been extracted
                } else {
                    remaining.add(bundleItem.copy());
                }
            }

            if (found) {
                // Rebuild the bundle without the extracted item
                bundleStack.set(DataComponents.BUNDLE_CONTENTS,
                        new BundleContents(remaining));
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Returns true if the given ItemStack is a shulker box item. */
    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private AutoRestock() {} // Utility class — no instantiation
}
