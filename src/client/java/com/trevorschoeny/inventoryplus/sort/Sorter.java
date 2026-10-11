package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.SORT;

import com.trevlar.menukit.api.window.SlotOperations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a sort to a list of slots in a container, using
 * {@link ContainerInput#PICKUP} clicks routed through
 * {@link MultiPlayerGameMode#handleContainerInput} so any server respects
 * them. The locked-slot shift-click synthesis works the same way.
 *
 * <h3>Algorithm</h3>
 *
 * <ol>
 *   <li><b>Filter.</b> Locked slots stay where they are, and sort operates
 *       only on unlocked slots in the region.</li>
 *   <li><b>Group.</b> Total the count per item and components across all
 *       unlocked slots, using {@link ItemStack#isSameItemSameComponents}
 *       for equality.</li>
 *   <li><b>Distribute.</b> For each group, split its total into
 *       max-stack-size chunks (full stacks + one partial remainder).
 *       Each chunk becomes one target stack.</li>
 *   <li><b>Sort the chunks</b> with the comparator the {@link SortType}
 *       carries. Empty slots trail.</li>
 *   <li><b>Apply.</b> Walk the target list slot-by-slot. For each
 *       target position, make the underlying inventory match by
 *       issuing PICKUP swaps and merges from later slots.</li>
 * </ol>
 *
 * <h3>Why three PICKUPs per swap</h3>
 *
 * Vanilla has no click that swaps two arbitrary slots, only the hotbar
 * SWAP, which is keyed to the keyboard. To swap slots A and B holding
 * different items:
 *
 * <ol>
 *   <li>PICKUP A: the cursor takes A's items and A is empty.</li>
 *   <li>PICKUP B: the cursor swaps with B (the different-items rule). The
 *       cursor has B's items and B has A's.</li>
 *   <li>PICKUP A: the cursor places into the empty A. A has B's items and
 *       the cursor is empty.</li>
 * </ol>
 *
 * <p>For same-item consolidation (32 dirt and 32 dirt into 64 dirt at the
 * primary slot), the second PICKUP merges instead of swapping, and the
 * order matters. See {@link #consolidateInto}.
 *
 * <h3>Sort types</h3>
 *
 * Every {@link SortType} carries its own comparator, so this class never
 * branches on the type. It groups, hands the groups to the comparator,
 * and splits the result into stacks. Adding a stop is a one-line
 * comparator on the enum with no change here.
 */
public final class Sorter {

    private Sorter() {}

    /**
     * Sorts the given region. The caller resolves the region (sortable
     * slots in the menu) and the type (usually from {@link SortState}).
     * Does nothing if the region has nothing to sort.
     */
    public static void sort(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
                            Player player, List<Slot> region, SortType type) {
        // Each stop owns its chunk ordering.
        Comparator<ItemStack> order = type.comparator();

        // A slot that refuses sorting stays put, and sort operates only on what
        // is left: dropped from this list, the slot is never read as a source
        // and never written as a destination. Locks refuse through Inventory
        // Plus's veto (Locks), so a locked slot or item sits out the same way.
        List<Slot> unlocked = new ArrayList<>();
        for (Slot s : region) {
            if (!SlotOperations.allows(menu, s, player, SORT)) continue;
            unlocked.add(s);
        }
        if (unlocked.size() < 2) return;

        List<ItemStack> target = computeTarget(unlocked, order);
        if (target.isEmpty()) return;

        // Every click the sort sends is judged as sorting, not as a plain click.
        SlotOperations.as(SORT, () -> applyTarget(menu, gameMode, player, unlocked, target));

        InventoryPlusClient.LOGGER.debug(
                "[sort] sorted {} unlocked slots with {}", unlocked.size(), type);
    }

    /** Steps 2 to 4: group, distribute, and sort with the type's comparator. */
    private static List<ItemStack> computeTarget(List<Slot> unlocked, Comparator<ItemStack> order) {
        // Total count per item and components. LinkedHashMap keeps insertion
        // order for stability, though the groups are re-sorted below.
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

        // Order the groups, not the individual stacks. Ranking loose stacks
        // strands an item's leftover partial away from its full stacks (a
        // 51-coal sorting next to a 53-iron rather than beside the 64-coal it
        // came from), which reads in-game as "it didn't sort". Sorting one
        // entry per item type and only then splitting into stacks keeps every
        // chunk of an item adjacent, whatever the sort type.
        //
        // Each entry is a representative stack whose count is the group's
        // total, so a comparator's getCount() means "how much of this item you
        // have". That total may exceed the item's max stack size; these stacks
        // are only ever compared and then discarded, never handed to the game.
        List<ItemStack> groups = new ArrayList<>();
        for (var entry : totals.entrySet()) {
            groups.add(entry.getKey().copyWithCount(entry.getValue()));
        }
        groups.sort(order);

        // Split each group into max-stack chunks, full stacks first.
        List<ItemStack> chunks = new ArrayList<>();
        for (ItemStack group : groups) {
            int remaining = group.getCount();
            int max = group.getMaxStackSize();
            while (remaining > 0) {
                int n = Math.min(remaining, max);
                chunks.add(group.copyWithCount(n));
                remaining -= n;
            }
        }

        // Pad with empties to match the unlocked slot count.
        while (chunks.size() < unlocked.size()) {
            chunks.add(ItemStack.EMPTY);
        }
        // If chunks > unlocked.size(), the algorithm input was inconsistent;
        // trim and log. It shouldn't happen, since chunks come from the same items.
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

    /** Step 5: walk the target slots in order, fixing each. */
    private static void applyTarget(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
                                     Player player, List<Slot> unlocked, List<ItemStack> target) {
        int n = unlocked.size();
        // Working model, updated after each click sequence to match what the
        // server is expected to do.
        ItemStack[] current = new ItemStack[n];
        for (int i = 0; i < n; i++) current[i] = unlocked.get(i).getItem().copy();

        for (int i = 0; i < n; i++) {
            ItemStack desired = target.get(i);

            if (matchesFull(current[i], desired)) continue;

            if (desired.isEmpty()) {
                // Slot i must end up empty. Find a later slot to absorb
                // current[i]'s items, preferring same-item slots
                // (consolidation) over empty ones.
                if (current[i].isEmpty()) continue;
                int dest = findCompatibleLater(current, i + 1, current[i]);
                if (dest == -1) dest = findEmptyLater(current, i + 1);
                if (dest == -1) continue; // unreachable for well-formed target
                doSwap(menu, gameMode, player, unlocked, current, i, dest);
                continue;
            }

            // Slot i should hold something: get the desired item type there.
            if (!ItemStack.isSameItemSameComponents(current[i], desired)) {
                int source = findSameItemLater(current, i + 1, desired);
                if (source == -1) continue; // unreachable
                doSwap(menu, gameMode, player, unlocked, current, i, source);
            }

            // Right item type now. If the count is low, pull from later same-item slots.
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
     * The usual three-PICKUP swap of two slots. It works for a different-item
     * swap (the cursor swaps under vanilla's PICKUP rules) and for same-item
     * consolidation (the cursor merges).
     *
     * <p>Afterwards the contents of slots {@code a} and {@code b} are
     * exchanged, or merged into one with the other left empty if they are the
     * same item type.
     */
    private static void doSwap(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
                                Player player, List<Slot> unlocked, ItemStack[] current,
                                int a, int b) {
        if (a == b) return;
        Slot slotA = unlocked.get(a);
        Slot slotB = unlocked.get(b);
        gameMode.handleContainerInput(menu.containerId, slotA.index, 0, ContainerInput.PICKUP, player);
        gameMode.handleContainerInput(menu.containerId, slotB.index, 0, ContainerInput.PICKUP, player);
        gameMode.handleContainerInput(menu.containerId, slotA.index, 0, ContainerInput.PICKUP, player);

        // Update the working model. Different items swap. The same item
        // merges into slotB (the second PICKUP target), with
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
     * Afterwards {@code dest} is filled up to its max stack size from
     * {@code source}, and any remainder stays at {@code source}.
     *
     * <p>PICKUP source takes its items onto the cursor. PICKUP dest merges
     * into dest (the same-item rule fills it to max and the cursor keeps the
     * remainder). PICKUP source puts any leftover back.
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
        gameMode.handleContainerInput(menu.containerId, slotSrc.index, 0, ContainerInput.PICKUP, player);
        gameMode.handleContainerInput(menu.containerId, slotDst.index, 0, ContainerInput.PICKUP, player);
        // The third PICKUP returns any leftover to the source. Safe even with
        // an empty cursor, since an empty PICKUP on an empty slot does nothing.
        gameMode.handleContainerInput(menu.containerId, slotSrc.index, 0, ContainerInput.PICKUP, player);

        int total = current[source].getCount() + current[dest].getCount();
        int max = current[source].getMaxStackSize();
        int intoDest = Math.min(total, max);
        int leftover = total - intoDest;
        current[dest] = current[source].copyWithCount(intoDest);
        current[source] = leftover > 0 ? current[source].copyWithCount(leftover) : ItemStack.EMPTY;
    }
}
