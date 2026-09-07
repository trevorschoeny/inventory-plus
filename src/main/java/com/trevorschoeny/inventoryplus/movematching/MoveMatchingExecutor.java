package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes Move Matching (IN or OUT) between the player inventory and the
 * open container, in one of the four {@link MoveMatchingMode}s.
 *
 * <h3>Direction</h3>
 *
 * Inventory-centric: {@code clickedGroup} is always the player main
 * inventory. IN makes it the destination (match-set = types already in
 * it, sources = every other non-hotbar slot); OUT makes it the source
 * (match-set and destinations = the other slots). The match-set always
 * comes from the receiving side.
 *
 * <h3>Modes, decided per item type</h3>
 *
 * Sources are grouped by item type and each type is handled whole:
 * <ul>
 *   <li><b>All</b>: every stack moves.</li>
 *   <li><b>But-one</b>: each stack leaves one item behind; a stack of 1
 *       is left as it is.</li>
 *   <li><b>But-one-stack</b>: one full stack's worth of the type stays.
 *       The largest stacks are kept first, so at most one stack is
 *       split, and a type with no more than a stack does not move.</li>
 *   <li><b>No-overflow</b>: the type moves only if the destination has
 *       room for all of it at the moment it is processed; otherwise none
 *       of it moves. Checked per type against current room, so a type
 *       that fits still moves even when another does not.</li>
 * </ul>
 *
 * <h3>Leaving some behind</h3>
 *
 * A left-click PICKUP lifts the whole stack, so "leave k" is: lift the
 * stack, right-click the now-empty source k times (each places one back),
 * then distribute the cursor. Same clicks a player would make.
 *
 * <h3>Why not QUICK_MOVE</h3>
 *
 * Vanilla's quick move routes to "the other half" and fills the hotbar
 * first in chest screens. Explicit PICKUPs place into chosen slots, stop
 * when the eligible ones are full, and honour the hotbar exclusion and
 * locked slots in both directions.
 */
public final class MoveMatchingExecutor {

    private MoveMatchingExecutor() {}

    public static void execute(Minecraft mc, SlotGroup clickedGroup, Direction direction, MoveMatchingMode mode) {
        if (!clickedGroup.targetable()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        Inventory playerInv = player.getInventory();
        List<Slot> otherSlots = collectOtherSlots(menu, clickedGroup, playerInv);

        List<Slot> matchSetSlots, sourceCandidates, destinationSlots;
        if (direction == Direction.IN) {
            matchSetSlots = clickedGroup.slots();
            sourceCandidates = otherSlots;
            destinationSlots = clickedGroup.slots();
        } else {
            matchSetSlots = otherSlots;
            sourceCandidates = new ArrayList<>(clickedGroup.slots());
            destinationSlots = otherSlots;
        }

        Set<Item> matchSet = buildMatchSet(matchSetSlots);
        if (matchSet.isEmpty()) {
            InventoryPlusClient.LOGGER.debug("[move-matching {} {}] match-set empty", direction, mode);
            return;
        }
        List<Slot> sources = filterToMatching(sourceCandidates, matchSet);
        if (sources.isEmpty()) {
            InventoryPlusClient.LOGGER.debug("[move-matching {} {}] nothing matching on the source side", direction, mode);
            return;
        }

        // Group by type, in source order, so a mode can decide a whole type at once.
        Map<Item, List<Slot>> byType = new LinkedHashMap<>();
        for (Slot s : sources) byType.computeIfAbsent(s.getItem().getItem(), k -> new ArrayList<>()).add(s);

        int stacksMoved = 0;
        for (Map.Entry<Item, List<Slot>> entry : byType.entrySet()) {
            stacksMoved += moveType(gameMode, player, menu, entry.getKey(), entry.getValue(), destinationSlots, mode);
        }
        InventoryPlusClient.LOGGER.debug("[move-matching {} {}] moved from {} stack(s)", direction, mode, stacksMoved);
    }

    /** Applies the mode to one item type's source stacks. Returns stacks that moved anything. */
    private static int moveType(MultiPlayerGameMode gameMode, LocalPlayer player, AbstractContainerMenu menu,
                                Item item, List<Slot> stacks, List<Slot> destinations, MoveMatchingMode mode) {
        int moved = 0;
        switch (mode) {
            case ALL -> {
                for (Slot s : stacks) if (moveSourceIntoSlots(gameMode, player, menu, s, destinations, 0) > 0) moved++;
            }
            case BUT_ONE -> {
                for (Slot s : stacks) {
                    if (s.getItem().getCount() <= 1) continue; // already at the minimum; untouched
                    if (moveSourceIntoSlots(gameMode, player, menu, s, destinations, 1) > 0) moved++;
                }
            }
            case BUT_ONE_STACK -> {
                // Keep the biggest stacks first so the kept amount costs at most one split.
                List<Slot> ordered = new ArrayList<>(stacks);
                ordered.sort(Comparator.comparingInt((Slot s) -> s.getItem().getCount()).reversed());
                int keep = ordered.get(0).getItem().getMaxStackSize();
                for (Slot s : ordered) {
                    int count = s.getItem().getCount();
                    if (keep >= count) { keep -= count; continue; }   // whole stack stays
                    int leave = keep;                                  // split this one, then keep nothing more
                    keep = 0;
                    if (moveSourceIntoSlots(gameMode, player, menu, s, destinations, leave) > 0) moved++;
                }
            }
            case NO_OVERFLOW -> {
                int total = 0;
                for (Slot s : stacks) total += s.getItem().getCount();
                int room = roomFor(stacks.get(0).getItem(), destinations);
                if (room < total) {
                    InventoryPlusClient.LOGGER.debug("[move-matching no-overflow] {} needs {}, room {}; left in place",
                            item, total, room);
                    return 0;
                }
                for (Slot s : stacks) if (moveSourceIntoSlots(gameMode, player, menu, s, destinations, 0) > 0) moved++;
            }
        }
        return moved;
    }

    /** Free room for {@code sample}'s type across unlocked destinations, as the game would fill them. */
    private static int roomFor(ItemStack sample, List<Slot> destinations) {
        int room = 0;
        for (Slot dest : destinations) {
            if (LockedSlots.isLockedSlot(dest)) continue;
            int limit = Math.min(dest.getMaxStackSize(sample), sample.getMaxStackSize());
            ItemStack in = dest.getItem();
            if (in.isEmpty()) room += limit;
            else if (in.is(sample.getItem())) room += Math.max(0, limit - in.getCount());
        }
        return room;
    }

    /**
     * Lifts the source stack, puts {@code leaveInSource} items back, then
     * merges into same-item destinations with room and fills empties.
     * Whatever still does not fit returns to the source.
     */
    private static int moveSourceIntoSlots(MultiPlayerGameMode gameMode, LocalPlayer player,
                                           AbstractContainerMenu menu, Slot source,
                                           List<Slot> destinationSlots, int leaveInSource) {
        int initialCount = source.getItem().getCount();
        if (leaveInSource >= initialCount) return 0;

        clickPickup(gameMode, player, menu, source.index);
        for (int i = 0; i < leaveInSource; i++) clickPlaceOne(gameMode, player, menu, source.index);

        Item cursorItem = menu.getCarried().getItem();

        for (Slot dest : destinationSlots) {
            if (menu.getCarried().isEmpty()) break;
            if (LockedSlots.isLockedSlot(dest)) continue;
            ItemStack destStack = dest.getItem();
            if (destStack.isEmpty() || !destStack.is(cursorItem)) continue;
            if (destStack.getCount() >= destStack.getMaxStackSize()) continue;
            clickPickup(gameMode, player, menu, dest.index);
        }
        for (Slot dest : destinationSlots) {
            if (menu.getCarried().isEmpty()) break;
            if (LockedSlots.isLockedSlot(dest)) continue;
            if (!dest.getItem().isEmpty()) continue;
            clickPickup(gameMode, player, menu, dest.index);
        }
        if (!menu.getCarried().isEmpty()) clickPickup(gameMode, player, menu, source.index);

        return Math.max(0, initialCount - source.getItem().getCount());
    }

    private static void clickPickup(MultiPlayerGameMode gameMode, LocalPlayer player,
                                    AbstractContainerMenu menu, int slotIndex) {
        gameMode.handleContainerInput(menu.containerId, slotIndex, 0, ContainerInput.PICKUP, player);
    }

    /** Right-click with a carried stack: places exactly one item into the slot. */
    private static void clickPlaceOne(MultiPlayerGameMode gameMode, LocalPlayer player,
                                      AbstractContainerMenu menu, int slotIndex) {
        gameMode.handleContainerInput(menu.containerId, slotIndex, 1, ContainerInput.PICKUP, player);
    }

    private static Set<Item> buildMatchSet(List<Slot> slots) {
        Set<Item> matchSet = new HashSet<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) matchSet.add(stack.getItem());
        }
        return matchSet;
    }

    /**
     * Candidates whose item is in the match-set. Locked slots and locked
     * items are never pulled from.
     *
     * <p>This one filter covers both directions, because it screens the
     * SOURCE side and the direction only decides which side that is: IN
     * will not pull a locked type out of the container, OUT will not push
     * one out of the inventory.
     *
     * <p>Destinations need no equivalent check. A locked type is filtered
     * out here, so it is never the thing being carried, and merging into a
     * locked stack could only happen while carrying its own type.
     */
    private static List<Slot> filterToMatching(List<Slot> candidates, Set<Item> matchSet) {
        List<Slot> filtered = new ArrayList<>();
        for (Slot slot : candidates) {
            if (LockedSlots.isLockedSlot(slot)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !matchSet.contains(stack.getItem())) continue;
            if (LockedItems.isLocked(stack)) continue;
            filtered.add(slot);
        }
        return filtered;
    }

    /** Every slot except the hotbar and the clicked group's own. */
    private static List<Slot> collectOtherSlots(AbstractContainerMenu menu, SlotGroup clickedGroup, Inventory playerInv) {
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
