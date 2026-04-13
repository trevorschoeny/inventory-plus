package com.trevorschoeny.inventoryplus.features.arrows;

import com.trevorschoeny.inventoryplus.InventoryPlus;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Deep Arrow Search: extends vanilla's projectile-finding logic to search
 * inside bundles, shulker boxes, and the ender chest.
 *
 * <p>Vanilla's {@code Player.getProjectile()} only scans top-level inventory
 * slots. This feature intercepts that method to also peek into containers,
 * returning a copy of any matching projectile found. The actual extraction
 * from the container is deferred until we confirm the shot was fired and
 * the projectile was consumed — this prevents arrow loss on cancelled shots
 * and correctly handles the Infinity enchantment.
 *
 * <p><b>Priority order:</b> loose inventory (vanilla handles this) →
 * bundles → shulker boxes → ender chest.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>{@link #peekContainers} — called from {@code getProjectile()} override.
 *       Finds a matching projectile in containers without modifying anything.
 *       Stores a {@link PendingContainerArrow} so we know where to extract later.</li>
 *   <li>{@link #resolveConsumption} — called from weapon RETURN injections
 *       (bow's releaseUsing, crossbow's tryLoadProjectiles). Checks whether
 *       the returned copy was actually consumed by vanilla's {@code useAmmo()}.
 *       If consumed (count went to 0 via split()), extracts from the container.
 *       If not consumed (Infinity), leaves the container untouched.</li>
 * </ol>
 *
 * @cairn 003-work-with-vanilla — peeks into vanilla components directly,
 *        lets vanilla's draw/useAmmo handle consumption, only modifies
 *        container contents after confirming vanilla consumed the projectile
 */
public final class DeepArrowSearch {

    private static final int SHULKER_SIZE = 27;

    /** Which type of container the arrow was found in. */
    public enum ContainerType { BUNDLE, SHULKER, ENDER_CHEST }

    /**
     * Records where we found a projectile during a peek, so we can extract
     * it later when we confirm consumption.
     *
     * @param type          which container type holds the arrow
     * @param containerSlot inventory slot of the bundle/shulker (-1 for ender chest)
     * @param innerIndex    index within the container's contents
     * @param arrowItem     the Item type captured at peek time — used for safe
     *                      verification during extraction. Cannot use arrowCopy
     *                      for this because getItem() returns AIR for empty stacks,
     *                      and arrowCopy will be empty after vanilla consumes it.
     * @param arrowCopy     the copy returned from getProjectile — vanilla's
     *                      useAmmo() will mutate this via split(), so checking
     *                      isEmpty() tells us if it was consumed
     */
    public record PendingContainerArrow(
            ContainerType type,
            int containerSlot,
            int innerIndex,
            Item arrowItem,
            ItemStack arrowCopy
    ) {}

    // Per-player pending state. WeakHashMap ensures cleanup on disconnect.
    // Only accessed from the server thread (within tick), so no sync needed.
    private static final WeakHashMap<Player, PendingContainerArrow> PENDING = new WeakHashMap<>();

    public static PendingContainerArrow getPending(Player player) {
        return PENDING.get(player);
    }

    public static void setPending(Player player, PendingContainerArrow pending) {
        PENDING.put(player, pending);
    }

    public static void clearPending(Player player) {
        PENDING.remove(player);
    }

    /**
     * Searches containers in the player's inventory for a projectile matching
     * the given predicate. Does NOT modify any container contents — this is
     * a read-only peek.
     *
     * <p>Priority: bundles → shulker boxes → ender chest. Within each
     * container type, scans in inventory slot order (lowest slot first).
     *
     * @param player    the player whose containers to search
     * @param predicate the projectile predicate (e.g., ARROW_ONLY for bows)
     * @return a pending record describing where the arrow was found,
     *         or null if no matching projectile exists in any container
     */
    public static PendingContainerArrow peekContainers(Player player, Predicate<ItemStack> predicate) {
        Inventory inv = player.getInventory();

        // 1. Search bundles
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack containerStack = inv.getItem(i);
            BundleContents contents = containerStack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents == null) continue;

            int index = 0;
            for (ItemStack bundleItem : contents.items()) {
                if (!bundleItem.isEmpty() && predicate.test(bundleItem)) {
                    return new PendingContainerArrow(
                            ContainerType.BUNDLE, i, index,
                            bundleItem.getItem(),
                            bundleItem.copyWithCount(1));
                }
                index++;
            }
        }

        // 2. Search shulker boxes
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack containerStack = inv.getItem(i);
            if (!isShulkerBox(containerStack)) continue;

            ItemContainerContents contents = containerStack.get(DataComponents.CONTAINER);
            if (contents == null) continue;

            NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
            contents.copyInto(items);

            for (int j = 0; j < items.size(); j++) {
                ItemStack shulkerItem = items.get(j);
                if (!shulkerItem.isEmpty() && predicate.test(shulkerItem)) {
                    return new PendingContainerArrow(
                            ContainerType.SHULKER, i, j,
                            shulkerItem.getItem(),
                            shulkerItem.copyWithCount(1));
                }
            }
        }

        // 3. Search ender chest
        var enderChest = player.getEnderChestInventory();
        for (int i = 0; i < enderChest.getContainerSize(); i++) {
            ItemStack item = enderChest.getItem(i);
            if (!item.isEmpty() && predicate.test(item)) {
                return new PendingContainerArrow(
                        ContainerType.ENDER_CHEST, -1, i,
                        item.getItem(),
                        item.copyWithCount(1));
            }
        }

        return null;
    }

    /**
     * Called after a weapon's shot/load completes. Checks whether the pending
     * arrow was actually consumed by vanilla's useAmmo() and extracts it from
     * the container if so.
     *
     * <p>The key insight: useAmmo() calls split() on the arrowCopy we returned
     * from getProjectile(). If consumed, split() reduces count to 0 (isEmpty).
     * If Infinity, useAmmo() calls copyWithCount() instead — the original is
     * untouched (count still 1, not empty).
     *
     * @param player the player who fired the shot
     */
    public static void resolveConsumption(Player player) {
        PendingContainerArrow pending = PENDING.remove(player);
        if (pending == null) return;

        // split() was called → count went to 0 → arrow was consumed
        if (pending.arrowCopy().isEmpty()) {
            extractFromContainer(player, pending);
        }
        // If not empty (Infinity), leave the arrow in the container
    }

    // ── Extraction ──────────────────────────────────────────────────────

    /**
     * Actually removes one arrow from the container identified by the pending
     * record. Verifies the arrow is still present before extracting — if the
     * container contents changed (e.g., player rearranged items), the
     * extraction is silently skipped.
     */
    private static void extractFromContainer(Player player, PendingContainerArrow pending) {
        switch (pending.type()) {
            case BUNDLE -> extractFromBundle(player, pending);
            case SHULKER -> extractFromShulker(player, pending);
            case ENDER_CHEST -> extractFromEnderChest(player, pending);
        }
    }

    private static void extractFromBundle(Player player, PendingContainerArrow pending) {
        Inventory inv = player.getInventory();
        ItemStack bundleStack = inv.getItem(pending.containerSlot());
        BundleContents contents = bundleStack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) return;

        // Rebuild the bundle contents, shrinking the target item by 1
        List<ItemStack> remaining = new ArrayList<>();
        int index = 0;
        boolean extracted = false;

        for (ItemStack bundleItem : contents.items()) {
            if (!extracted && index == pending.innerIndex()
                    && bundleItem.is(pending.arrowItem())) {
                // Found the arrow — shrink by 1
                if (bundleItem.getCount() > 1) {
                    ItemStack shrunk = bundleItem.copy();
                    shrunk.shrink(1);
                    remaining.add(shrunk);
                }
                // If count was 1, omit entirely (fully extracted)
                extracted = true;
            } else {
                remaining.add(bundleItem.copy());
            }
            index++;
        }

        if (extracted) {
            bundleStack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(remaining));
            InventoryPlus.LOGGER.debug(
                    "[DeepArrowSearch] Extracted arrow from bundle in slot {}",
                    pending.containerSlot());
        }
    }

    private static void extractFromShulker(Player player, PendingContainerArrow pending) {
        Inventory inv = player.getInventory();
        ItemStack shulkerStack = inv.getItem(pending.containerSlot());
        if (!isShulkerBox(shulkerStack)) return;

        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return;

        NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        contents.copyInto(items);

        ItemStack target = items.get(pending.innerIndex());
        if (target.isEmpty() || !target.is(pending.arrowItem())) return;

        target.shrink(1);
        shulkerStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));

        InventoryPlus.LOGGER.debug(
                "[DeepArrowSearch] Extracted arrow from shulker in slot {}",
                pending.containerSlot());
    }

    private static void extractFromEnderChest(Player player, PendingContainerArrow pending) {
        var enderChest = player.getEnderChestInventory();
        ItemStack target = enderChest.getItem(pending.innerIndex());
        if (target.isEmpty() || !target.is(pending.arrowItem())) return;

        if (target.getCount() <= 1) {
            enderChest.setItem(pending.innerIndex(), ItemStack.EMPTY);
        } else {
            target.shrink(1);
            enderChest.setChanged();
        }

        InventoryPlus.LOGGER.debug(
                "[DeepArrowSearch] Extracted arrow from ender chest slot {}",
                pending.innerIndex());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private DeepArrowSearch() {} // Utility class — no instantiation
}
