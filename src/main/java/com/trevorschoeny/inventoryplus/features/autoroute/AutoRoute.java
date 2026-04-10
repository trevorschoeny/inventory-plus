package com.trevorschoeny.inventoryplus.features.autoroute;

import com.trevorschoeny.inventoryplus.InventoryPlus;
import com.trevorschoeny.menukit.container.MKContainer;
import com.trevorschoeny.menukit.data.MKInventory;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/**
 * AutoRoute: intercepts item pickup and routes items INTO shulker boxes
 * and bundles that already contain that item type.
 *
 * <p>This is the complement to {@code AutoFill}, which pulls items OUT of
 * shulker boxes. AutoRoute does the reverse: when the player picks up items,
 * it checks if any shulker box or bundle in their inventory already contains
 * that item type, and inserts the picked-up items there instead of leaving
 * them as loose inventory items.
 *
 * <p><b>Design decisions:</b>
 * <ul>
 *   <li>Uses {@code ItemStack.isSameItemSameComponents} for matching, so
 *       enchanted or custom-named items only route to containers holding
 *       identical copies. This prevents accidentally mixing variants.</li>
 *   <li>Shulker boxes are checked first, then bundles. Within each type,
 *       containers are scanned in inventory order (slots 0-35).</li>
 *   <li>Shulker boxes that ARE the item being picked up are skipped — you
 *       can't route a shulker box into another shulker box.</li>
 *   <li>Bundle weight limits are respected via {@code BundleContents.Mutable}
 *       and its {@code tryInsert()} method.</li>
 *   <li>All methods are pure server-side logic. The entry point is
 *       {@link #routePickup(Inventory, ItemStack)}, called from the mixin.</li>
 * </ul>
 */
public class AutoRoute {

    private static final int SHULKER_SIZE = 27;

    /**
     * Attempts to route a picked-up item into shulker boxes and bundles
     * that already contain the same item type.
     *
     * <p>Modifies {@code incoming} in place — shrinks its count as items
     * are routed into containers. If fully consumed, the caller should
     * cancel the default pickup (vanilla's Inventory.add).
     *
     * <p>As of decision 010, this scans every player-owned container that
     * participates in auto-pickup routing — vanilla hotbar/main/armor/offhand
     * plus any MK-registered player-bound container that didn't opt out via
     * {@code .excludeFromAutoPickup()}. Pockets participate by default;
     * equipment slots opt out.
     *
     * @param inventory the player's vanilla inventory (used only to reach the Player)
     * @param incoming  the item being picked up (mutated: count is reduced)
     * @return true if the entire stack was consumed (incoming is now empty)
     */
    public static boolean routePickup(Inventory inventory, ItemStack incoming) {
        if (incoming.isEmpty()) return true;

        // Don't route shulker boxes into other shulker boxes — that would be
        // confusing and shulkers can't nest inside each other anyway.
        if (isShulkerBox(incoming)) return false;

        Player player = inventory.player;

        // Pull every auto-pickup-eligible container across the player's storage.
        // This automatically includes vanilla (hotbar/main/armor/offhand) and
        // any player-bound MK container that didn't opt out — i.e., pockets by
        // default. See decision 010.
        List<MKContainer> targets = MKInventory.getAutoPickupContainers(player);

        // Pass 1: Try to insert into shulker boxes that already contain this item.
        // We prefer shulkers first because they have fixed-size slots (predictable
        // capacity) and are the most common bulk storage container.
        if (!incoming.isEmpty()) {
            routeIntoShulkers(targets, incoming);
        }

        // Pass 2: Try to insert into bundles that already contain this item.
        // Bundles have weight-based storage, so we use the Mutable API to
        // respect capacity limits.
        if (!incoming.isEmpty()) {
            routeIntoBundles(targets, incoming);
        }

        boolean fullyConsumed = incoming.isEmpty();
        if (fullyConsumed) {
            InventoryPlus.LOGGER.debug("[AutoRoute] Fully routed pickup into containers");
        } else if (incoming.getCount() < incoming.getMaxStackSize()) {
            // Partial routing happened — log it for debugging
            InventoryPlus.LOGGER.debug("[AutoRoute] Partially routed pickup, {} remaining",
                    incoming.getCount());
        }

        return fullyConsumed;
    }

    // ── Shulker Box Routing ─────────────────────────────────────────────

    /**
     * Scans all shulker boxes across every target container and inserts
     * matching items from {@code incoming}. Modifies both the shulker
     * contents (via the immutable read-modify-write pattern) and the
     * incoming stack count.
     *
     * <p>For each shulker box found in any target container, we:
     * <ol>
     *   <li>Read its {@link ItemContainerContents} into a mutable list</li>
     *   <li>Check if ANY slot contains a matching item (containment check)</li>
     *   <li>If yes, try to merge into existing partial stacks first</li>
     *   <li>Then fill empty slots with remaining items</li>
     *   <li>Write back the modified contents</li>
     * </ol>
     */
    private static void routeIntoShulkers(List<MKContainer> targets, ItemStack incoming) {
        for (MKContainer container : targets) {
            if (incoming.isEmpty()) return;

            boolean containerModified = false;

            for (int i = 0; i < container.getContainerSize() && !incoming.isEmpty(); i++) {
                ItemStack containerStack = container.getItem(i);
                if (!isShulkerBox(containerStack)) continue;

                ItemContainerContents contents = containerStack.get(DataComponents.CONTAINER);
                if (contents == null) continue;

                // Read the shulker's items into a mutable list
                NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
                contents.copyInto(items);

                // Containment check: only route into this shulker if it already
                // contains the same item type. We don't want to arbitrarily stuff
                // items into random shulkers — only ones the player has already
                // organized to hold this item.
                if (!containsMatchingItem(items, incoming)) continue;

                boolean modified = false;

                // Sub-pass A: merge into existing partial stacks of the same item.
                // This is the most space-efficient — fills gaps in existing stacks
                // before consuming any new slots.
                for (int j = 0; j < items.size() && !incoming.isEmpty(); j++) {
                    ItemStack shulkerItem = items.get(j);
                    if (shulkerItem.isEmpty()) continue;
                    if (!ItemStack.isSameItemSameComponents(shulkerItem, incoming)) continue;

                    // This slot has a matching item — top it off
                    int maxSize = shulkerItem.getMaxStackSize();
                    int space = maxSize - shulkerItem.getCount();
                    if (space <= 0) continue;

                    int toInsert = Math.min(space, incoming.getCount());
                    shulkerItem.grow(toInsert);
                    incoming.shrink(toInsert);
                    modified = true;
                }

                // Sub-pass B: place remaining items into empty slots.
                // Only if partial-stack merging didn't consume everything.
                for (int j = 0; j < items.size() && !incoming.isEmpty(); j++) {
                    if (!items.get(j).isEmpty()) continue;

                    // Found an empty slot — place items here
                    int toPlace = Math.min(incoming.getMaxStackSize(), incoming.getCount());
                    items.set(j, incoming.copyWithCount(toPlace));
                    incoming.shrink(toPlace);
                    modified = true;
                }

                // Write back the modified contents (immutable component pattern)
                if (modified) {
                    containerStack.set(DataComponents.CONTAINER,
                            ItemContainerContents.fromItems(items));
                    containerModified = true;
                }
            }

            // Notify the container that its contents changed so sync/save hooks
            // fire. For vanilla inventory this is largely redundant (vanilla
            // tracks via setChanged on Inventory), but for MK player-bound
            // containers (pockets) it's necessary — mutating the stack in place
            // wouldn't otherwise trigger the container's onChange callback.
            if (containerModified) {
                container.setChanged();
            }
        }
    }

    // ── Bundle Routing ──────────────────────────────────────────────────

    /**
     * Scans all bundles across every target container and inserts matching
     * items from {@code incoming}. Uses the {@code BundleContents.Mutable}
     * API to respect bundle weight limits.
     *
     * <p>Bundles use weight-based storage: each item costs
     * {@code 64 / maxStackSize} weight units, with a total capacity of 64.
     * We use {@code tryInsert()} which handles weight calculation internally.
     */
    private static void routeIntoBundles(List<MKContainer> targets, ItemStack incoming) {
        for (MKContainer container : targets) {
            if (incoming.isEmpty()) return;

            boolean containerModified = false;

            for (int i = 0; i < container.getContainerSize() && !incoming.isEmpty(); i++) {
                ItemStack bundleStack = container.getItem(i);
                if (!isBundle(bundleStack)) continue;

                BundleContents contents = bundleStack.get(DataComponents.BUNDLE_CONTENTS);
                if (contents == null) continue;

                // Containment check: only route into bundles that already hold
                // a matching item. Same philosophy as shulkers — respect the
                // player's organizational intent.
                if (!bundleContainsItem(contents, incoming)) continue;

                // Use the Mutable API to insert items respecting weight limits.
                // tryInsert() returns the number of items actually inserted (0 if full).
                BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
                int inserted = mutable.tryInsert(incoming);

                if (inserted > 0) {
                    // tryInsert already shrinks `incoming` by `inserted` amount,
                    // so we just need to write back the modified bundle contents.
                    bundleStack.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
                    containerModified = true;
                }
            }

            // Fire sync/save hooks for MK-backed containers (see note in
            // routeIntoShulkers). No-op for vanilla inventory in practice.
            if (containerModified) {
                container.setChanged();
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Checks if any item in the shulker's slot list matches the incoming item.
     * Uses {@code isSameItemSameComponents} for exact matching (same item, same
     * enchantments, same custom name, etc.).
     */
    private static boolean containsMatchingItem(NonNullList<ItemStack> items, ItemStack target) {
        for (ItemStack item : items) {
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a bundle already contains an item matching the incoming item.
     * Iterates the bundle's item list using {@code isSameItemSameComponents}.
     */
    private static boolean bundleContainsItem(BundleContents contents, ItemStack target) {
        for (ItemStack item : contents.items()) {
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, target)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if the given ItemStack is a shulker box item. */
    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    /** Returns true if the given ItemStack is a bundle. */
    private static boolean isBundle(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Bundles are identified by having the BUNDLE_CONTENTS component.
        // This handles both regular bundles and any modded bundle variants.
        return stack.has(DataComponents.BUNDLE_CONTENTS);
    }
}
