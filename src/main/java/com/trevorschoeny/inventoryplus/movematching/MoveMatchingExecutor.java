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
 * Executes move-matching for a clicked / triggered slot group.
 *
 * <h3>Direction (Trev 2026-05-15)</h3>
 *
 * The clicked group is the <b>target</b>. Items flow INTO it from every
 * other slot in the open menu, with two exclusions:
 *
 * <ul>
 *   <li><b>Hotbar</b> — never a source AND never a destination
 *       (deliberately differs from vanilla shift-click, which overflows
 *       into the hotbar when main inv fills up).</li>
 *   <li><b>The target group itself</b> — items already in the target
 *       define the match-set; their own slots aren't sources to themselves.</li>
 * </ul>
 *
 * <p>All other visible slot groups are eligible sources, including
 * non-traditional groups like furnace fuel, brewing-stand bottles, or
 * donkey saddle — per Trev's permissive-source direction. They just
 * can't be targets themselves.
 *
 * <h3>Match-set</h3>
 *
 * Built from items currently in the target group's slots, by item ID
 * (components / enchantments / NBT ignored — per Trev 2026-05-16, two
 * stacks of the same item match regardless of enchantment).
 *
 * <h3>Why not QUICK_MOVE</h3>
 *
 * An earlier version used {@link ClickType#QUICK_MOVE} (shift-click).
 * Vanilla's {@code quickMoveStack} on chest screens fills the player
 * inventory from the hotbar end first, meaning items overflowed into
 * the hotbar when the player chose "match into inventory" — exactly
 * what Trev's spec excludes. Manual PICKUP / PICKUP sequences let us
 * place into specific target slots and stop when the target group's
 * non-hotbar slots are full.
 *
 * <h3>Cycle stops</h3>
 *
 * <ul>
 *   <li>{@link MoveMatchingCycle#ALL_MATCHING} — every match flows,
 *       including non-stackables.</li>
 *   <li>{@link MoveMatchingCycle#STACKABLE_ONLY} — match-set filtered to
 *       stackable items at both sides.</li>
 *   <li>{@link MoveMatchingCycle#DISABLED} — no-op.</li>
 * </ul>
 */
public final class MoveMatchingExecutor {

    private MoveMatchingExecutor() {}

    public static void execute(Minecraft mc, SlotGroup target, MoveMatchingCycle cycle) {
        if (cycle == MoveMatchingCycle.DISABLED) return;
        // Targetability re-check is implicit — the caller (MoveMatchingButtons
        // or MoveMatchingKeybind) already validated that `target.targetable(screen)`
        // before invoking the executor. We don't re-validate here because the
        // executor doesn't know the screen, and the only invariant the
        // executor relies on is target.slots() being non-empty and target.key()
        // being meaningful — both follow from "targetable on the live screen".

        LocalPlayer player = mc.player;
        if (player == null) return;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        Inventory playerInv = player.getInventory();

        Set<Item> matchSet = buildMatchSet(target, cycle);
        if (matchSet.isEmpty()) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] target has no items — no-op");
            return;
        }

        // Collect source slots first so the iteration is stable across
        // the click sequence (each click mutates menu state).
        List<Slot> sourceSlots = collectSourceSlots(menu, target, playerInv, matchSet, cycle);

        int totalMoved = 0;
        for (Slot source : sourceSlots) {
            ItemStack stackBefore = source.getItem();
            if (stackBefore.isEmpty()) continue;
            // Re-check match — defensive in case state changed.
            if (!matchSet.contains(stackBefore.getItem())) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stackBefore.getMaxStackSize() <= 1) continue;

            int moved = moveSourceIntoTarget(mc, gameMode, player, menu, source, target);
            if (moved > 0) totalMoved++;
        }

        InventoryPlusClient.LOGGER.debug(
                "[move-matching] target.key={} cycle={} processed {} source slot(s)",
                target.key() != null ? target.key().toKeyString() : "<no-key>",
                cycle, totalMoved);
    }

    /**
     * Picks up the source slot's stack and places it onto target group
     * slots until the cursor is empty or no more eligible destinations
     * exist. If anything remains on the cursor after the placement loop,
     * we put it back onto the source slot so nothing is left dangling.
     *
     * <p>Eligible destinations: same-item-with-space slots first (merges,
     * keeps stacks consolidated), then empty slots (preserves natural
     * slot order). Each destination is re-checked live because each
     * click changes the slot state.
     *
     * @return non-zero if any portion of the source stack landed in the
     *         target group; zero if no destination took anything.
     */
    private static int moveSourceIntoTarget(Minecraft mc,
                                            MultiPlayerGameMode gameMode,
                                            LocalPlayer player,
                                            AbstractContainerMenu menu,
                                            Slot source,
                                            SlotGroup target) {
        int initialCount = source.getItem().getCount();

        // PICKUP source — cursor now holds the source's stack.
        clickPickup(gameMode, player, menu, source.index);

        // Pass 1: merge into existing same-item slots in target.
        Item cursorItem = menu.getCarried().getItem();
        for (Slot dest : target.slots()) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            ItemStack destStack = dest.getItem();
            if (destStack.isEmpty()) continue;
            if (!destStack.is(cursorItem)) continue;
            if (destStack.getCount() >= destStack.getMaxStackSize()) continue;
            clickPickup(gameMode, player, menu, dest.index);
        }

        // Pass 2: place remainder into empty slots in target.
        for (Slot dest : target.slots()) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            ItemStack destStack = dest.getItem();
            if (!destStack.isEmpty()) continue;
            clickPickup(gameMode, player, menu, dest.index);
        }

        // If anything's left on the cursor (target was full or only
        // had different-item slots), drop it back onto the source slot.
        if (!menu.getCarried().isEmpty()) {
            clickPickup(gameMode, player, menu, source.index);
        }

        int finalCount = source.getItem().getCount();
        return Math.max(0, initialCount - finalCount);
    }

    /**
     * Single LEFT-click (PICKUP, button=0) on the given slot. Vanilla's
     * {@link ClickType#PICKUP} semantics:
     *
     * <ul>
     *   <li>Empty cursor + non-empty slot → take whole stack to cursor.</li>
     *   <li>Non-empty cursor + empty slot → place whole cursor on slot.</li>
     *   <li>Non-empty cursor + same-item slot → merge until slot full,
     *       overflow stays on cursor.</li>
     *   <li>Non-empty cursor + different-item slot → swap.</li>
     * </ul>
     *
     * Our placement loop only invokes this on (a) the source slot
     * itself (to pick up / put back) and (b) destination slots that
     * we've checked are empty or same-item-with-space — so the
     * different-item swap case never fires from this executor.
     */
    private static void clickPickup(MultiPlayerGameMode gameMode, LocalPlayer player,
                                    AbstractContainerMenu menu, int slotIndex) {
        gameMode.handleInventoryMouseClick(
                menu.containerId,
                slotIndex,
                /* mouseButton */ 0,
                ClickType.PICKUP,
                player);
    }

    /**
     * Items currently in the target group, filtered by cycle. For
     * STACKABLE_ONLY, non-stackable items don't contribute to the match
     * set (so a single non-stackable in the chest doesn't pull other
     * non-stackables from inventory).
     */
    private static Set<Item> buildMatchSet(SlotGroup target, MoveMatchingCycle cycle) {
        Set<Item> matchSet = new HashSet<>();
        for (Slot slot : target.slots()) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stack.getMaxStackSize() <= 1) continue;
            matchSet.add(stack.getItem());
        }
        return matchSet;
    }

    /**
     * Source slots = every slot in the menu EXCEPT (a) hotbar and (b)
     * the target group's own slots. Filtered to slots that currently
     * hold a match-set item.
     */
    private static List<Slot> collectSourceSlots(AbstractContainerMenu menu,
                                                 SlotGroup target,
                                                 Inventory playerInv,
                                                 Set<Item> matchSet,
                                                 MoveMatchingCycle cycle) {
        List<Slot> sources = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (isHotbarSlot(slot, playerInv)) continue;
            if (target.containsMenuIndex(slot.index)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (!matchSet.contains(stack.getItem())) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stack.getMaxStackSize() <= 1) continue;
            sources.add(slot);
        }
        return sources;
    }

    /**
     * Hotbar = slots in the player's main inventory with container-slot
     * index in [0, 9). Per spec + Trev's redirect, never a source AND
     * never a destination.
     */
    private static boolean isHotbarSlot(Slot slot, Inventory playerInv) {
        if (slot.container != playerInv) return false;
        int ci = slot.getContainerSlot();
        return ci >= 0 && ci < 9;
    }
}
