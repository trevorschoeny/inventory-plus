package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Executes Move Matching (IN or OUT) for a clicked / triggered slot
 * group.
 *
 * <h3>Direction semantics</h3>
 *
 * Both directions share the same underlying machinery — the clicked
 * group plays a different role per direction:
 *
 * <ul>
 *   <li><b>{@link Direction#IN}</b> — clicked group is the <i>destination</i>.
 *       Match-set = items in the clicked group. Sources = every other
 *       visible slot (excluding hotbar). For each matching source slot,
 *       push into the clicked group's slots.</li>
 *   <li><b>{@link Direction#OUT}</b> — clicked group is the <i>source</i>.
 *       Match-set = items in every OTHER visible slot (excluding hotbar)
 *       — i.e., item types that have a "home" elsewhere. For each
 *       matching slot in the clicked group, push out to those other
 *       slots.</li>
 * </ul>
 *
 * <p>The "match-set comes from the receiving side" invariant holds for
 * both directions — that's why a chest's IN button and the inventory's
 * OUT button do the same operation from different entry points (and
 * symmetrically for chest's OUT vs inventory's IN).
 *
 * <h3>Hotbar exclusion</h3>
 *
 * Hotbar is never a source AND never a destination, in either direction.
 * Per Trev's 2026-05-15 redirect, deliberately differs from vanilla
 * shift-click (which overflows into hotbar when main inv fills).
 *
 * <h3>Why not QUICK_MOVE</h3>
 *
 * Vanilla {@code quickMoveStack} routes via "the other half of the
 * screen" — for chest screens it fills the player inventory hotbar end
 * first, then main inv. Manual PICKUP / PICKUP sequences let us place
 * into specific destination slots and stop when the eligible
 * destinations are full, respecting the hotbar exclusion in both
 * directions.
 *
 * <h3>Cycle stops</h3>
 *
 * <ul>
 *   <li>{@link MoveMatchingCycle#ALL_MATCHING} — every match flows,
 *       including non-stackables.</li>
 *   <li>{@link MoveMatchingCycle#STACKABLE_ONLY} — match-set filtered to
 *       stackable items, on both the match-build and source-filter
 *       sides.</li>
 *   <li>{@link MoveMatchingCycle#DISABLED} — no-op (the executor returns
 *       early; the widget still cycles to escape).</li>
 * </ul>
 */
public final class MoveMatchingExecutor {

    private MoveMatchingExecutor() {}

    public static void execute(Minecraft mc, SlotGroup clickedGroup,
                               Direction direction, MoveMatchingCycle cycle) {
        if (cycle == MoveMatchingCycle.DISABLED) return;
        if (!clickedGroup.targetable()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        Inventory playerInv = player.getInventory();

        // The "other slots" set is used in both directions:
        //   - IN: as the SOURCE candidates (match-set comes from clickedGroup)
        //   - OUT: as the MATCH-SET source AND the DESTINATIONS
        List<Slot> otherSlots = collectOtherSlots(menu, clickedGroup, playerInv);

        List<Slot> matchSetSlots;
        List<Slot> sourceCandidates;
        List<Slot> destinationSlots;

        if (direction == Direction.IN) {
            matchSetSlots = clickedGroup.slots();
            sourceCandidates = otherSlots;
            destinationSlots = clickedGroup.slots();
        } else { // OUT
            matchSetSlots = otherSlots;
            sourceCandidates = new ArrayList<>(clickedGroup.slots());
            destinationSlots = otherSlots;
        }

        Set<Item> matchSet = buildMatchSet(matchSetSlots, cycle);
        if (matchSet.isEmpty()) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching {}] match-set empty — no-op", direction);
            return;
        }

        // Filter sources to those holding a match-set item.
        List<Slot> sources = filterToMatching(sourceCandidates, matchSet, cycle);
        if (sources.isEmpty()) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching {}] no matching items on the source side — no-op",
                    direction);
            return;
        }

        int totalMoved = 0;
        for (Slot source : sources) {
            ItemStack stackBefore = source.getItem();
            if (stackBefore.isEmpty()) continue;
            // Defensive re-check: state may have changed across previous iterations.
            if (!matchSet.contains(stackBefore.getItem())) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stackBefore.getMaxStackSize() <= 1) continue;

            int moved = moveSourceIntoSlots(gameMode, player, menu, source, destinationSlots);
            if (moved > 0) totalMoved++;
        }

        InventoryPlusClient.LOGGER.debug(
                "[move-matching {}] clicked.key={} cycle={} processed {} source slot(s)",
                direction,
                clickedGroup.key() != null ? clickedGroup.key().toKeyString() : "<no-key>",
                cycle, totalMoved);
    }

    /**
     * Moves the source slot's stack into the given destination slot
     * list, preferring same-item-with-space slots first (merges), then
     * empty slots. Anything that doesn't fit is put back on the source.
     */
    private static int moveSourceIntoSlots(MultiPlayerGameMode gameMode,
                                           LocalPlayer player,
                                           AbstractContainerMenu menu,
                                           Slot source,
                                           List<Slot> destinationSlots) {
        int initialCount = source.getItem().getCount();

        clickPickup(gameMode, player, menu, source.index);

        Item cursorItem = menu.getCarried().getItem();

        // Pass 1: merge into existing same-item destinations with space.
        for (Slot dest : destinationSlots) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            ItemStack destStack = dest.getItem();
            if (destStack.isEmpty()) continue;
            if (!destStack.is(cursorItem)) continue;
            if (destStack.getCount() >= destStack.getMaxStackSize()) continue;
            clickPickup(gameMode, player, menu, dest.index);
        }

        // Pass 2: place remainder into empty destinations.
        for (Slot dest : destinationSlots) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            if (!dest.getItem().isEmpty()) continue;
            clickPickup(gameMode, player, menu, dest.index);
        }

        // Leftover — put back on source.
        if (!menu.getCarried().isEmpty()) {
            clickPickup(gameMode, player, menu, source.index);
        }

        return Math.max(0, initialCount - source.getItem().getCount());
    }

    private static void clickPickup(MultiPlayerGameMode gameMode, LocalPlayer player,
                                    AbstractContainerMenu menu, int slotIndex) {
        gameMode.handleInventoryMouseClick(
                menu.containerId,
                slotIndex,
                /* mouseButton */ 0,
                ClickType.PICKUP,
                player);
    }

    /** Items in the given slots, filtered by cycle. */
    private static Set<Item> buildMatchSet(List<Slot> slots, MoveMatchingCycle cycle) {
        Set<Item> matchSet = new HashSet<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stack.getMaxStackSize() <= 1) continue;
            matchSet.add(stack.getItem());
        }
        return matchSet;
    }

    /**
     * Returns slots from the candidate list whose current item is in
     * the match-set (with the STACKABLE_ONLY filter applied).
     */
    private static List<Slot> filterToMatching(List<Slot> candidates,
                                               Set<Item> matchSet,
                                               MoveMatchingCycle cycle) {
        List<Slot> filtered = new ArrayList<>();
        for (Slot slot : candidates) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (!matchSet.contains(stack.getItem())) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stack.getMaxStackSize() <= 1) continue;
            filtered.add(slot);
        }
        return filtered;
    }

    /**
     * Every slot in the menu EXCEPT (a) hotbar and (b) the clicked
     * group's own slots. Used for both directions:
     *
     * <ul>
     *   <li>IN: as the source candidates.</li>
     *   <li>OUT: as the match-set source AND the destinations.</li>
     * </ul>
     */
    private static List<Slot> collectOtherSlots(AbstractContainerMenu menu,
                                                SlotGroup clickedGroup,
                                                Inventory playerInv) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (isHotbarSlot(slot, playerInv)) continue;
            if (clickedGroup.containsMenuIndex(slot.index)) continue;
            result.add(slot);
        }
        return result;
    }

    private static boolean isHotbarSlot(Slot slot, Inventory playerInv) {
        if (slot.container != playerInv) return false;
        int ci = slot.getContainerSlot();
        return ci >= 0 && ci < 9;
    }
}
