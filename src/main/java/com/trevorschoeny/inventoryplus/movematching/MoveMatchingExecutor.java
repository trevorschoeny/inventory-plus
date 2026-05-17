package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

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
 * Executes Move Matching (IN or OUT) between the player inventory and
 * the open container.
 *
 * <h3>Direction semantics (Trev / Lead 2026-05-16 simplification)</h3>
 *
 * Move Matching is now <b>inventory-centric</b> — buttons live on the
 * player inventory only, and every operation pairs the inventory with
 * the single open container. The {@code clickedGroup} passed in is
 * always the player main inventory; the executor uses
 * {@link Direction} to decide which side is source vs destination:
 *
 * <ul>
 *   <li><b>{@link Direction#IN}</b> — inventory is the <i>destination</i>.
 *       Match-set = items in the inventory ("matches" = item types
 *       already in inv). Sources = every other visible slot (excluding
 *       hotbar). For each matching source slot, push into the inventory.</li>
 *   <li><b>{@link Direction#OUT}</b> — inventory is the <i>source</i>.
 *       Match-set = items in every OTHER visible slot (excluding hotbar)
 *       — i.e., item types the container already has. For each matching
 *       slot in the inventory, push out to the other slots.</li>
 * </ul>
 *
 * <p>The "match-set comes from the receiving side" invariant holds for
 * both directions — IN's receiver is inv, OUT's receiver is container.
 *
 * <h3>No cycle / no disable</h3>
 *
 * The previous 3-stop cycle (ALL/STACKABLE/DISABLED) was removed in the
 * 2026-05-16 simplification. The operation always behaves like the old
 * "ALL_MATCHING" stop — every matching item type moves, including
 * non-stackables. Protection is delegated to Locked Slots / Locked
 * Items (filed in DEFERRED.md until those features land).
 *
 * <h3>Hotbar exclusion</h3>
 *
 * Hotbar is never a source AND never a destination. Spec §"Scope":
 * "Hotbar excluded by default — Move Matching doesn't pull from or push
 * to hotbar slots." A future config toggle will allow inclusion (see
 * DEFERRED.md).
 *
 * <h3>Why not QUICK_MOVE</h3>
 *
 * Vanilla {@code quickMoveStack} routes via "the other half of the
 * screen" and fills the hotbar end first for chest screens. Manual
 * PICKUP / PICKUP sequences let us place into specific destination
 * slots and stop when the eligible destinations are full, respecting
 * the hotbar exclusion in both directions.
 */
public final class MoveMatchingExecutor {

    private MoveMatchingExecutor() {}

    public static void execute(Minecraft mc, SlotGroup clickedGroup, Direction direction) {
        if (!clickedGroup.targetable()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        Inventory playerInv = player.getInventory();

        // The "other slots" set — used in both directions:
        //   IN:  source candidates (match-set comes from clickedGroup)
        //   OUT: match-set source AND destinations
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

        Set<Item> matchSet = buildMatchSet(matchSetSlots);
        if (matchSet.isEmpty()) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching {}] match-set empty — no-op", direction);
            return;
        }

        List<Slot> sources = filterToMatching(sourceCandidates, matchSet);
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
            if (!matchSet.contains(stackBefore.getItem())) continue;
            int moved = moveSourceIntoSlots(gameMode, player, menu, source, destinationSlots);
            if (moved > 0) totalMoved++;
        }

        InventoryPlusClient.LOGGER.debug(
                "[move-matching {}] processed {} source slot(s)",
                direction, totalMoved);
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
        // Locked player slots skipped per spec §"Locked Slots" — "a locked
        // target slot is not filled."
        for (Slot dest : destinationSlots) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            if (LockedSlots.isLockedSlot(dest)) continue;
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
            if (LockedSlots.isLockedSlot(dest)) continue;
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

    /** Items present in the given slots — keyed by Item identity. */
    private static Set<Item> buildMatchSet(List<Slot> slots) {
        Set<Item> matchSet = new HashSet<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            matchSet.add(stack.getItem());
        }
        return matchSet;
    }

    /**
     * Slots from the candidate list whose current item is in the
     * match-set. Locked player slots are excluded per spec §"Locked
     * Slots" — "a locked source slot is not pulled from."
     */
    private static List<Slot> filterToMatching(List<Slot> candidates, Set<Item> matchSet) {
        List<Slot> filtered = new ArrayList<>();
        for (Slot slot : candidates) {
            if (LockedSlots.isLockedSlot(slot)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (!matchSet.contains(stack.getItem())) continue;
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
