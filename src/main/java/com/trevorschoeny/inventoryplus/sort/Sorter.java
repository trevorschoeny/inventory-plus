package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a sort to a list of slots in a container, using
 * {@link ClickType#PICKUP} clicks routed through
 * {@link MultiPlayerGameMode#handleInventoryMouseClick} so the server
 * universally respects them — same mechanism as the locked-slot
 * shift-click synthesis.
 *
 * <h3>Algorithm</h3>
 *
 * <ol>
 *   <li><b>Filter.</b> Locked slots stay where they are; sort operates
 *       only on unlocked slots in the region.</li>
 *   <li><b>Group.</b> Collect (item, components) → total count across
 *       all unlocked slots, preserving same-item-same-components
 *       equality via {@link ItemStack#isSameItemSameComponents}.</li>
 *   <li><b>Distribute.</b> For each group, split its total into
 *       max-stack-size chunks (full stacks + one partial remainder).
 *       Each chunk becomes one target stack.</li>
 *   <li><b>Sort the chunks.</b> Currently {@link
 *       SortType#QUANTITY_DESC} only — count descending, tiebreaker
 *       item-ID ascending. Empty slots trail.</li>
 *   <li><b>Apply.</b> Walk the target list slot-by-slot. For each
 *       target position, ensure the underlying inventory matches by
 *       issuing PICKUP swaps and merges from later slots.</li>
 * </ol>
 *
 * <h3>Why three PICKUPs per swap</h3>
 *
 * Vanilla has no "swap arbitrary slots" click — only hotbar SWAP, and
 * that's keyboard-keyed. To swap slots A and B with different items:
 *
 * <ol>
 *   <li>PICKUP A → cursor takes A's items; A empty.</li>
 *   <li>PICKUP B → cursor swaps with B (different items rule). Cursor
 *       has B's items; B has A's items.</li>
 *   <li>PICKUP A → cursor places at A (now empty). A has B's items;
 *       cursor empty.</li>
 * </ol>
 *
 * <p>For same-item consolidation (e.g., 32 dirt + 32 dirt → 64 dirt at
 * the primary slot), the second PICKUP merges instead of swaps, and
 * the order matters — see {@link #consolidateInto}.
 *
 * <h3>MVP scope</h3>
 *
 * Only {@link SortType#QUANTITY_DESC} is implemented. Other types
 * throw {@link UnsupportedOperationException} — they're wired in the
 * type/persistence layer but won't be reachable until the type-cycle
 * power-user feature lands.
 */
public final class Sorter {

    private Sorter() {}

    /**
     * Sorts the given region. Caller resolves the region (sortable
     * slots in the menu) and the type (from {@link SortState} or
     * caller-driven). No-ops if the type is {@link SortType#DISABLED}
     * or the region has nothing to sort.
     */
    public static void sort(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
                            Player player, List<Slot> region, SortType type) {
        if (type == SortType.DISABLED) return;
        if (type != SortType.QUANTITY_DESC) {
            throw new UnsupportedOperationException(
                    "Sort type " + type + " not yet implemented (MVP: QUANTITY_DESC only)");
        }

        // Locked slots stay put — sort operates only on unlocked.
        List<Slot> unlocked = new ArrayList<>();
        for (Slot s : region) {
            if (!LockedSlots.isLockedSlot(s)) unlocked.add(s);
        }
        if (unlocked.size() < 2) return;

        // Compute target layout.
        List<ItemStack> target = computeTarget(unlocked, type);
        if (target.isEmpty()) return;

        // Apply by issuing PICKUP click sequences.
        applyTarget(menu, gameMode, player, unlocked, target);

        InventoryPlusClient.LOGGER.debug(
                "[sort] sorted {} unlocked slots with {}", unlocked.size(), type);
    }

    /** Step 2-4: group → distribute → sort. */
    private static List<ItemStack> computeTarget(List<Slot> unlocked, SortType type) {
        // Group total count per (item, components). LinkedHashMap keeps insertion
        // order for stability, though we re-sort below.
        Map<ItemStack, Integer> totals = new LinkedHashMap<>();
        for (Slot s : unlocked) {
            ItemStack stack = s.getItem();
            if (stack.isEmpty()) continue;
            ItemStack key = findKey(totals, stack);
            if (key == null) {
                totals.put(stack.copyWithCount(1), stack.getCount());
            } else {
                totals.merge(key, stack.getCount(), Integer::sum);
            }
        }

        // Distribute each group's total into max-stack chunks.
        List<ItemStack> chunks = new ArrayList<>();
        for (var entry : totals.entrySet()) {
            ItemStack key = entry.getKey();
            int remaining = entry.getValue();
            int max = key.getMaxStackSize();
            while (remaining > 0) {
                int n = Math.min(remaining, max);
                chunks.add(key.copyWithCount(n));
                remaining -= n;
            }
        }

        // Sort.
        if (type == SortType.QUANTITY_DESC) {
            chunks.sort(Comparator
                    .<ItemStack>comparingInt(s -> -s.getCount())
                    .thenComparing(Sorter::idOf));
        }

        // Pad with empties to match the unlocked slot count.
        while (chunks.size() < unlocked.size()) {
            chunks.add(ItemStack.EMPTY);
        }
        // If chunks > unlocked.size(), the algorithm input was inconsistent;
        // trim and log (shouldn't happen since chunks come from the same items).
        if (chunks.size() > unlocked.size()) {
            InventoryPlusClient.LOGGER.warn(
                    "[sort] target chunks ({}) exceed unlocked slots ({}) — trimming",
                    chunks.size(), unlocked.size());
            chunks = chunks.subList(0, unlocked.size());
        }
        return chunks;
    }

    /** Find an existing key in totals that matches stack by item + components. */
    private static ItemStack findKey(Map<ItemStack, Integer> totals, ItemStack stack) {
        for (ItemStack key : totals.keySet()) {
            if (ItemStack.isSameItemSameComponents(key, stack)) return key;
        }
        return null;
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Step 5: apply by walking target slots in order, fixing each. */
    private static void applyTarget(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
                                     Player player, List<Slot> unlocked, List<ItemStack> target) {
        int n = unlocked.size();
        // Working model — updated after each click sequence to stay in sync
        // with what we expect the server to do.
        ItemStack[] current = new ItemStack[n];
        for (int i = 0; i < n; i++) current[i] = unlocked.get(i).getItem().copy();

        for (int i = 0; i < n; i++) {
            ItemStack desired = target.get(i);

            // Already correct?
            if (matchesFull(current[i], desired)) continue;

            if (desired.isEmpty()) {
                // Need slot i empty. Find a later slot to absorb current[i]'s
                // items — prefer same-item slots (consolidation), fall back to
                // empty slots.
                if (current[i].isEmpty()) continue;
                int dest = findCompatibleLater(current, i + 1, current[i]);
                if (dest == -1) dest = findEmptyLater(current, i + 1);
                if (dest == -1) continue; // unreachable for well-formed target
                doSwap(menu, gameMode, player, unlocked, current, i, dest);
                continue;
            }

            // Desired non-empty: make sure slot i has the desired item type.
            if (!ItemStack.isSameItemSameComponents(current[i], desired)) {
                int source = findSameItemLater(current, i + 1, desired);
                if (source == -1) continue; // unreachable
                doSwap(menu, gameMode, player, unlocked, current, i, source);
            }

            // Right item type now. Fix count if low — pull from later same-item slots.
            // Excess (count too high) will be processed naturally at the next target
            // position where this item type appears.
            int needed = desired.getCount() - current[i].getCount();
            while (needed > 0) {
                int source = findSameItemLater(current, i + 1, desired);
                if (source == -1) break;
                consolidateInto(menu, gameMode, player, unlocked, current, source, i);
                int newNeeded = desired.getCount() - current[i].getCount();
                if (newNeeded == needed) break; // no progress, avoid infinite loop
                needed = newNeeded;
            }
        }
    }

    private static boolean matchesFull(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() != b.isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }

    private static int findSameItemLater(ItemStack[] current, int start, ItemStack target) {
        for (int j = start; j < current.length; j++) {
            if (!current[j].isEmpty()
                    && ItemStack.isSameItemSameComponents(current[j], target)) {
                return j;
            }
        }
        return -1;
    }

    private static int findCompatibleLater(ItemStack[] current, int start, ItemStack target) {
        return findSameItemLater(current, start, target);
    }

    private static int findEmptyLater(ItemStack[] current, int start) {
        for (int j = start; j < current.length; j++) {
            if (current[j].isEmpty()) return j;
        }
        return -1;
    }

    /**
     * Standard 3-PICKUP swap of two slots — works for both
     * different-item swap (cursor swaps via vanilla's PICKUP rules)
     * and same-item consolidation (cursor merges).
     *
     * <p>End state: contents of slot {@code a} and slot {@code b} are
     * exchanged (or merged into one if same item type, with the other
     * left empty).
     */
    private static void doSwap(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
                                Player player, List<Slot> unlocked, ItemStack[] current,
                                int a, int b) {
        if (a == b) return;
        Slot slotA = unlocked.get(a);
        Slot slotB = unlocked.get(b);
        gameMode.handleInventoryMouseClick(menu.containerId, slotA.index, 0, ClickType.PICKUP, player);
        gameMode.handleInventoryMouseClick(menu.containerId, slotB.index, 0, ClickType.PICKUP, player);
        gameMode.handleInventoryMouseClick(menu.containerId, slotA.index, 0, ClickType.PICKUP, player);

        // Update working model based on what actually happened.
        // For different items: pure swap.
        // For same item: merge into slotB (the second PICKUP target), with
        // any cursor remainder returning to slotA on the third PICKUP.
        if (ItemStack.isSameItemSameComponents(current[a], current[b]) && !current[a].isEmpty()) {
            int total = current[a].getCount() + current[b].getCount();
            int max = current[a].getMaxStackSize();
            int intoB = Math.min(total, max);
            int leftover = total - intoB;
            current[b] = current[a].copyWithCount(intoB);
            current[a] = leftover > 0 ? current[a].copyWithCount(leftover) : ItemStack.EMPTY;
        } else {
            ItemStack tmp = current[a];
            current[a] = current[b];
            current[b] = tmp;
        }
    }

    /**
     * Merge items from {@code source} into {@code dest} (same item type).
     * After: {@code dest} is filled up to its max stack size from
     * {@code source}; remainder (if any) stays at {@code source}.
     *
     * <p>Sequence: PICKUP source → cursor takes source's items. PICKUP
     * dest → cursor merges into dest (same item rule, fills to max,
     * cursor keeps remainder). PICKUP source → if cursor has leftover,
     * place back at source.
     */
    private static void consolidateInto(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
                                         Player player, List<Slot> unlocked, ItemStack[] current,
                                         int source, int dest) {
        if (source == dest) return;
        if (current[source].isEmpty()) return;
        if (!ItemStack.isSameItemSameComponents(current[source], current[dest])
                && !current[dest].isEmpty()) {
            // Caller should have checked. Fall back to a regular swap.
            doSwap(menu, gameMode, player, unlocked, current, dest, source);
            return;
        }

        Slot slotSrc = unlocked.get(source);
        Slot slotDst = unlocked.get(dest);
        gameMode.handleInventoryMouseClick(menu.containerId, slotSrc.index, 0, ClickType.PICKUP, player);
        gameMode.handleInventoryMouseClick(menu.containerId, slotDst.index, 0, ClickType.PICKUP, player);
        // Third PICKUP returns leftover (if any) to source. Safe even if cursor
        // is empty — empty PICKUP on empty slot is a no-op.
        gameMode.handleInventoryMouseClick(menu.containerId, slotSrc.index, 0, ClickType.PICKUP, player);

        // Update model.
        int total = current[source].getCount() + current[dest].getCount();
        int max = current[source].getMaxStackSize();
        int intoDest = Math.min(total, max);
        int leftover = total - intoDest;
        current[dest] = current[source].copyWithCount(intoDest);
        current[source] = leftover > 0 ? current[source].copyWithCount(leftover) : ItemStack.EMPTY;
    }
}
