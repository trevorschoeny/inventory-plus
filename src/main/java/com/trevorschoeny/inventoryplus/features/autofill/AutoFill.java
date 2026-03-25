package com.trevorschoeny.inventoryplus.features.autofill;

import com.trevorschoeny.inventoryplus.InventoryPlus;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Autofill: scans the player's inventory for partial stacks and fills
 * them from shulker boxes in the same inventory. Also fills empty hotbar
 * slots if items of the same type exist elsewhere in the hotbar.
 *
 * <p>All methods are pure server-side logic. The entry point is
 * {@link #execute(ServerPlayer)}, called from the C2S packet handler.
 *
 * <p><b>Design decisions:</b>
 * <ul>
 *   <li>Uses {@code ItemStack.isSameItemSameComponents} for matching,
 *       so enchanted or custom-named items only match identical copies.
 *       This prevents accidentally merging different enchantment levels.</li>
 *   <li>Processes inventory slots 0-35 (hotbar + main inventory). Armor
 *       and offhand are excluded — they rarely have stackable items.</li>
 *   <li>Empty hotbar slots are only filled if the same item type already
 *       exists in another hotbar slot — this avoids pulling random items
 *       the player doesn't intend to use.</li>
 *   <li>Shulker contents are modified via the immutable
 *       {@link ItemContainerContents} component (read-modify-write),
 *       matching the pattern used by Block Palette.</li>
 * </ul>
 */
public class AutoFill {

    private static final int SHULKER_SIZE = 27;
    // Inventory slots 0-8 are hotbar, 9-35 are main inventory
    private static final int HOTBAR_END = 9;
    private static final int INVENTORY_SIZE = 36;

    /**
     * Executes autofill for the given player. Called from the server
     * thread via the C2S packet handler.
     *
     * <p>Two passes:
     * <ol>
     *   <li><b>Top-off pass</b>: find partial stacks and fill them
     *       from shulker boxes</li>
     *   <li><b>Empty hotbar pass</b>: fill empty hotbar slots with
     *       items matching what's already in the hotbar</li>
     * </ol>
     *
     * @param player the server-side player whose inventory to autofill
     * @return the total number of items transferred (for logging)
     */
    public static int execute(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int totalTransferred = 0;

        // ── Pass 1: Top off partial stacks ──────────────────────────────
        // Scan every inventory slot for items that aren't at max stack size.
        // For each, search shulker boxes for matching items to extract.
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;

            int maxSize = stack.getMaxStackSize();
            if (stack.getCount() >= maxSize) continue;

            // This stack is partial — try to fill it from shulkers
            int needed = maxSize - stack.getCount();
            int extracted = extractFromShulkers(inventory, stack, needed);
            if (extracted > 0) {
                stack.grow(extracted);
                totalTransferred += extracted;
            }
        }

        // ── Pass 2: Fill empty hotbar slots ─────────────────────────────
        // Collect the set of item types currently in the hotbar. For each
        // empty hotbar slot, we won't know which item to fill it with —
        // but if there's only one type of item in shulkers that matches
        // something in the hotbar, we can fill empty slots with it.
        //
        // Strategy: for each item type in the hotbar, check if there are
        // extras in shulker boxes. If so, pull a full stack into an empty slot.
        Set<Item> hotbarItems = getHotbarItemTypes(inventory);
        List<Integer> emptyHotbarSlots = getEmptyHotbarSlots(inventory);

        for (int emptySlot : emptyHotbarSlots) {
            // Try each hotbar item type — first match wins
            for (Item item : hotbarItems) {
                ItemStack pulled = pullFullStackFromShulkers(inventory, item);
                if (!pulled.isEmpty()) {
                    inventory.setItem(emptySlot, pulled);
                    totalTransferred += pulled.getCount();
                    break; // filled this slot, move to next empty slot
                }
            }
        }

        if (totalTransferred > 0) {
            InventoryPlus.LOGGER.debug("[AutoFill] Transferred {} items for {}",
                    totalTransferred, player.getName().getString());
        }

        return totalTransferred;
    }

    // ── Shulker Extraction ──────────────────────────────────────────────

    /**
     * Extracts up to {@code maxAmount} items matching {@code target} from
     * shulker boxes in the player's inventory.
     *
     * <p>Iterates all 36 inventory slots looking for shulker boxes. For
     * each shulker, reads its {@link ItemContainerContents}, finds matching
     * items, shrinks them, and writes the updated contents back.
     *
     * @param inventory the player's inventory
     * @param target    the item to match (uses isSameItemSameComponents)
     * @param maxAmount maximum number of items to extract
     * @return the actual number of items extracted
     */
    private static int extractFromShulkers(Inventory inventory, ItemStack target, int maxAmount) {
        int remaining = maxAmount;

        for (int i = 0; i < INVENTORY_SIZE && remaining > 0; i++) {
            ItemStack containerStack = inventory.getItem(i);
            if (!isShulkerBox(containerStack)) continue;

            ItemContainerContents contents = containerStack.get(DataComponents.CONTAINER);
            if (contents == null) continue;

            // Read the shulker's items into a mutable list
            NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
            contents.copyInto(items);

            boolean modified = false;
            for (int j = 0; j < items.size() && remaining > 0; j++) {
                ItemStack shulkerItem = items.get(j);
                if (shulkerItem.isEmpty()) continue;

                // Match by item type AND components (enchantments, custom name, etc.)
                // so we don't accidentally merge items with different properties
                if (!ItemStack.isSameItemSameComponents(shulkerItem, target)) continue;

                // Take as many as we need (or as many as the slot has)
                int take = Math.min(remaining, shulkerItem.getCount());
                shulkerItem.shrink(take);
                remaining -= take;
                modified = true;
            }

            // Write back the modified contents if anything changed.
            // fromItems handles empty stacks correctly (they become empty slots).
            if (modified) {
                containerStack.set(DataComponents.CONTAINER,
                        ItemContainerContents.fromItems(items));
            }
        }

        return maxAmount - remaining;
    }

    /**
     * Pulls a full stack of the given item type from shulker boxes.
     * Used by the empty-hotbar-fill pass to create new stacks.
     *
     * <p>Unlike {@link #extractFromShulkers}, this matches by item type
     * only (ignoring components) because we're filling an empty slot —
     * any variant of the item is acceptable. It takes the first matching
     * item it finds and extracts up to maxStackSize.
     *
     * @param inventory the player's inventory
     * @param item      the item type to look for
     * @return the extracted stack, or {@link ItemStack#EMPTY} if none found
     */
    private static ItemStack pullFullStackFromShulkers(Inventory inventory, Item item) {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack containerStack = inventory.getItem(i);
            if (!isShulkerBox(containerStack)) continue;

            ItemContainerContents contents = containerStack.get(DataComponents.CONTAINER);
            if (contents == null) continue;

            NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
            contents.copyInto(items);

            // Find the first matching item in this shulker
            for (int j = 0; j < items.size(); j++) {
                ItemStack shulkerItem = items.get(j);
                if (shulkerItem.isEmpty() || !shulkerItem.is(item)) continue;

                // Found a match — build a result stack up to max size,
                // pulling from this slot and potentially subsequent matching slots
                ItemStack result = shulkerItem.copy();
                int maxSize = result.getMaxStackSize();

                // Take all from this slot first
                int taken = Math.min(maxSize, shulkerItem.getCount());
                result.setCount(taken);
                shulkerItem.shrink(taken);

                // Continue scanning this shulker for more of the same
                // item to fill the stack completely
                for (int k = j + 1; k < items.size() && result.getCount() < maxSize; k++) {
                    ItemStack other = items.get(k);
                    if (other.isEmpty()) continue;
                    if (!ItemStack.isSameItemSameComponents(other, result)) continue;

                    int take = Math.min(maxSize - result.getCount(), other.getCount());
                    result.grow(take);
                    other.shrink(take);
                }

                // Write back modified shulker contents
                containerStack.set(DataComponents.CONTAINER,
                        ItemContainerContents.fromItems(items));

                return result;
            }
        }

        return ItemStack.EMPTY;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Returns the set of distinct item types currently in the hotbar. */
    private static Set<Item> getHotbarItemTypes(Inventory inventory) {
        Set<Item> types = new HashSet<>();
        for (int i = 0; i < HOTBAR_END; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                types.add(stack.getItem());
            }
        }
        return types;
    }

    /** Returns indices of empty hotbar slots (0-8). */
    private static List<Integer> getEmptyHotbarSlots(Inventory inventory) {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < HOTBAR_END; i++) {
            if (inventory.getItem(i).isEmpty()) {
                empty.add(i);
            }
        }
        return empty;
    }

    /** Returns true if the given ItemStack is a shulker box item. */
    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }
}
