package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.carried.CarriedPutClient;
import com.trevorschoeny.inventoryplus.nested.CarriedPut;
import com.trevorschoeny.inventoryplus.operations.CursorShelf;
import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.MOVE_MATCHING_IN;
import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.MOVE_MATCHING_OUT;

import com.trevlar.slotkit.api.window.SlotOperations;

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
 * inventory. IN makes it the destination (the match set is the types
 * already in it, and the sources are every other non-hotbar slot). OUT makes
 * it the source (the match set and destinations are the other slots, and an
 * item goes only into a container that already holds it). The match set
 * always comes from the receiving side. The hotbar joins the player's side
 * where Reach allows it, which by default it does not.
 *
 * <h3>Modes, decided per item type</h3>
 *
 * Sources are grouped by item type and each type is handled whole:
 * <ul>
 *   <li><b>All</b>: every stack moves.</li>
 *   <li><b>But-one</b>: each stack leaves one item behind, and a stack of 1
 *       is left as it is.</li>
 *   <li><b>But-one-stack</b>: one full stack's worth of the type stays.
 *       The largest stacks are kept first, so at most one stack is
 *       split, and a type with no more than a stack does not move.</li>
 *   <li><b>No-overflow</b>: the type moves only if the destination has
 *       room for all of it at the moment it is processed, otherwise none
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
 * when the eligible ones are full, and honor Reach and locks in both
 * directions.
 */
public final class MoveMatchingExecutor {

    private MoveMatchingExecutor() {}

    public static void execute(Minecraft mc, SlotGroup clickedGroup, Direction direction, MoveMatchingMode mode) {
        execute(mc, clickedGroup, direction, mode, true);
    }

    /**
     * @param intoCarried In only: first put the sources into the carried containers that
     *                    hold their kind ({@code CarriedPut}, container first), then, once
     *                    the server has answered, run again without this for the rest
     */
    private static void execute(Minecraft mc, SlotGroup clickedGroup, Direction direction, MoveMatchingMode mode,
                                boolean intoCarried) {
        if (!clickedGroup.targetable()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        Inventory playerInv = player.getInventory();
        List<Slot> otherSlots = collectOtherSlots(menu, clickedGroup, playerInv);
        // The player's side is the main inventory and the hotbar. Reach leaves
        // the hotbar out of both keys by default (Move Matching's Hotbar box),
        // so it takes part only once the player ticks it there.
        List<Slot> hotbar = hotbarSlots(menu, playerInv);
        List<Slot> playerSide = new ArrayList<>(clickedGroup.slots());
        playerSide.addAll(hotbar);

        List<Slot> matchSetSlots, sourceCandidates, destinationSlots;
        if (direction == Direction.IN) {
            // The hotbar's kinds count only where Move Matching may fill it.
            matchSetSlots = new ArrayList<>(clickedGroup.slots());
            for (Slot h : hotbar) if (SlotOperations.allows(menu, h, player, MOVE_MATCHING_IN)) matchSetSlots.add(h);
            sourceCandidates = otherSlots;
            destinationSlots = playerSide;
        } else {
            matchSetSlots = otherSlots;
            sourceCandidates = playerSide;
            destinationSlots = otherSlots;
        }

        Set<Item> matchSet = buildMatchSet(matchSetSlots);
        // Container first: what the carried containers Move Matching may fill hold counts
        // as already in the inventory, so a shulker of cobblestone pulls cobblestone.
        boolean carried = direction == Direction.IN && CarriedPutClient.moveMatchingInto();
        if (carried) matchSet.addAll(CarriedPutClient.moveMatchingKinds(player));
        if (matchSet.isEmpty()) {
            InventoryPlusClient.LOGGER.debug("[move-matching {} {}] match-set empty", direction, mode);
            return;
        }
        List<Slot> sources = filterToMatching(sourceCandidates, matchSet, menu, player);
        if (sources.isEmpty()) {
            InventoryPlusClient.LOGGER.debug("[move-matching {} {}] nothing matching on the source side", direction, mode);
            return;
        }

        // Group by type, in source order, so a mode can decide a whole type at once.
        Map<Item, List<Slot>> byType = new LinkedHashMap<>();
        for (Slot s : sources) byType.computeIfAbsent(s.getItem().getItem(), k -> new ArrayList<>()).add(s);

        if (carried && intoCarried) {
            // First pass, on the server: each source gives what the mode would move to the
            // containers that hold its kind. The second pass reads what's left.
            List<CarriedPut.Source> first = new ArrayList<>();
            for (List<Slot> stacks : byType.values()) {
                leaves(stacks, mode).forEach((slot, leave) ->
                        first.add(new CarriedPut.Source(slot.index, slot.getItem().getCount() - leave)));
            }
            CarriedPutClient.moveIn(menu.containerId, first, () -> {
                if (mc.player != null && mc.player.containerMenu == menu) {
                    execute(mc, clickedGroup, direction, mode, false);
                }
            });
            return;
        }

        // The "in" key is about the slot being filled, whichever way the player
        // pointed Move Matching, so destinations are screened for it once here.
        List<Slot> allowedDestinations = new ArrayList<>(destinationSlots.size());
        for (Slot d : destinationSlots) {
            if (SlotOperations.allows(menu, d, player, MOVE_MATCHING_IN)) allowedDestinations.add(d);
        }
        // The moves lift each source onto the cursor, so a stack already
        // held would be put down in the first source instead. It is set
        // aside outside every slot this run can touch, and handed back after.
        List<Slot> touched = new ArrayList<>(sources);
        touched.addAll(allowedDestinations);
        int[] stacksMoved = {0};
        CursorShelf.runWithEmptyCursor(menu, gameMode, player, touched, () -> {
            for (Map.Entry<Item, List<Slot>> entry : byType.entrySet()) {
                // Out, an item goes only into a container that already holds it, so
                // with several containers on screen (a chest's container list) each
                // keeps to its own kinds. In, the destination is the inventory alone.
                List<Slot> destinations = direction == Direction.IN ? allowedDestinations
                        : holding(entry.getKey(), allowedDestinations, matchSetSlots);
                stacksMoved[0] += moveType(gameMode, player, menu, entry.getKey(), entry.getValue(), destinations, mode);
            }
        });
        InventoryPlusClient.LOGGER.debug("[move-matching {} {}] moved from {} stack(s)", direction, mode, stacksMoved[0]);
    }

    /**
     * The destinations in a container that holds {@code item} somewhere among
     * {@code containerSlots}. A container is a slot's {@code slot.container}:
     * every slot group is its own (SlotKit 2.1), so a box opened in a chest's
     * list is one container and the chest another.
     */
    private static List<Slot> holding(Item item, List<Slot> destinations, List<Slot> containerSlots) {
        Set<Object> holders = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Slot s : containerSlots) if (s.getItem().is(item)) holders.add(s.container);
        List<Slot> out = new ArrayList<>();
        for (Slot d : destinations) if (holders.contains(d.container)) out.add(d);
        return out;
    }

    /** Applies the mode to one item type's source stacks. Returns stacks that moved anything. */
    private static int moveType(MultiPlayerGameMode gameMode, LocalPlayer player, AbstractContainerMenu menu,
                                Item item, List<Slot> stacks, List<Slot> destinations, MoveMatchingMode mode) {
        if (mode == MoveMatchingMode.NO_OVERFLOW) {
            int total = 0;
            for (Slot s : stacks) total += s.getItem().getCount();
            int room = roomFor(menu, stacks.get(0).getItem(), destinations);
            if (room < total) {
                InventoryPlusClient.LOGGER.debug("[move-matching no-overflow] {} needs {}, room {}; left in place",
                        item, total, room);
                return 0;
            }
        }
        int moved = 0;
        for (Map.Entry<Slot, Integer> e : leaves(stacks, mode).entrySet()) {
            if (moveSourceIntoSlots(gameMode, player, menu, e.getKey(), destinations, e.getValue()) > 0) moved++;
        }
        return moved;
    }

    /**
     * The stacks of one item type the mode moves, each with how many it leaves behind;
     * a stack the mode leaves whole is absent. No-overflow moves everything (whether it
     * fits is the caller's check).
     */
    private static Map<Slot, Integer> leaves(List<Slot> stacks, MoveMatchingMode mode) {
        Map<Slot, Integer> out = new LinkedHashMap<>();
        switch (mode) {
            case ALL, NO_OVERFLOW -> { for (Slot s : stacks) out.put(s, 0); }
            case BUT_ONE -> {
                // A stack of 1 is already at the minimum, untouched.
                for (Slot s : stacks) if (s.getItem().getCount() > 1) out.put(s, 1);
            }
            case BUT_ONE_STACK -> {
                // Keep the biggest stacks first so the kept amount costs at most one split.
                List<Slot> ordered = new ArrayList<>(stacks);
                ordered.sort(Comparator.comparingInt((Slot s) -> s.getItem().getCount()).reversed());
                int keep = ordered.get(0).getItem().getMaxStackSize();
                for (Slot s : ordered) {
                    int count = s.getItem().getCount();
                    if (keep >= count) { keep -= count; continue; }   // whole stack stays
                    out.put(s, keep);                                  // split this one, then keep nothing more
                    keep = 0;
                }
            }
        }
        return out;
    }

    /**
     * Free room for {@code sample}'s type across the destinations, as the game would fill
     * them. An empty destination that would refuse {@code sample} (a shulker box's own slots
     * refuse another shulker box) offers none, so No-overflow doesn't count room it can't
     * actually use.
     */
    private static int roomFor(AbstractContainerMenu menu, ItemStack sample, List<Slot> destinations) {
        int room = 0;
        for (Slot dest : destinations) {
            int limit = Math.min(dest.getMaxStackSize(sample), sample.getMaxStackSize());
            ItemStack in = dest.getItem();
            if (in.isEmpty()) {
                if (IPSlotOperations.mayAccept(menu, dest, sample)) room += limit;
            } else if (in.is(sample.getItem())) {
                room += Math.max(0, limit - in.getCount());
            }
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

        // Lifting the stack and putting the kept items back both belong to
        // taking from the source, so both go under the out key on each side. If
        // the put-back were judged by the source's own "in" rule, a lock that
        // refused Move Matching in could strand the kept items on the cursor.
        SlotOperations.as(MOVE_MATCHING_OUT, MOVE_MATCHING_OUT, () -> {
            clickPickup(gameMode, player, menu, source.index);
            for (int i = 0; i < leaveInSource; i++) clickPlaceOne(gameMode, player, menu, source.index);
        });

        Item cursorItem = menu.getCarried().getItem();

        // Filling the destinations: the in key is judged on them.
        SlotOperations.as(MOVE_MATCHING_OUT, MOVE_MATCHING_IN, () -> {
            for (Slot dest : destinationSlots) {
                if (menu.getCarried().isEmpty()) break;
                ItemStack destStack = dest.getItem();
                if (destStack.isEmpty() || !destStack.is(cursorItem)) continue;
                if (destStack.getCount() >= destStack.getMaxStackSize()) continue;
                clickPickup(gameMode, player, menu, dest.index);
            }
            for (Slot dest : destinationSlots) {
                if (menu.getCarried().isEmpty()) break;
                if (!dest.getItem().isEmpty()) continue;
                if (!IPSlotOperations.mayAccept(menu, dest, menu.getCarried())) continue;
                clickPickup(gameMode, player, menu, dest.index);
            }
        });
        // What did not fit goes home: part of taking, as above.
        if (!menu.getCarried().isEmpty()) {
            SlotOperations.as(MOVE_MATCHING_OUT, MOVE_MATCHING_OUT,
                    () -> clickPickup(gameMode, player, menu, source.index));
        }

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
     * Candidates whose item is in the match-set and whose slot allows Move
     * Matching out. Locks refuse through the veto (Locks), so a locked slot or
     * a locked item is never pulled from, in either direction: the direction
     * only decides which side is the source.
     */
    private static List<Slot> filterToMatching(List<Slot> candidates, Set<Item> matchSet,
                                               AbstractContainerMenu menu, LocalPlayer player) {
        List<Slot> filtered = new ArrayList<>();
        for (Slot slot : candidates) {
            // The out key is about the slot being emptied, whichever way the
            // player pointed Move Matching.
            if (!SlotOperations.allows(menu, slot, player, MOVE_MATCHING_OUT)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !matchSet.contains(stack.getItem())) continue;
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

    /** The player's hotbar slots on {@code menu}, in order. */
    private static List<Slot> hotbarSlots(AbstractContainerMenu menu, Inventory playerInv) {
        List<Slot> out = new ArrayList<>(9);
        for (Slot slot : menu.slots) if (isHotbarSlot(slot, playerInv)) out.add(slot);
        return out;
    }

    private static boolean isHotbarSlot(Slot slot, Inventory playerInv) {
        if (slot.container != playerInv) return false;
        int ci = slot.getContainerSlot();
        return ci >= 0 && ci < 9;
    }
}
