package com.trevorschoeny.inventoryplus.operations;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Runs a click sequence planned for an empty cursor even when the player is
 * already holding something. Sort and Move Matching swap two slots with
 * three PICKUPs, using the cursor as scratch space (and, for same-item
 * consolidation, its deposit-merge rule); a real held stack would otherwise
 * be the first thing a click puts down.
 *
 * <p>Sets the held stack aside on an empty main-inventory or hotbar slot
 * that {@code action} never reads or writes, runs {@code action}
 * with the cursor genuinely empty, then picks the held stack back up. If no
 * such slot takes it (none is empty and untouched, or something like a lock
 * refuses every candidate), the held stack is left exactly as it was and
 * {@code action} does not run: there is nowhere to run it safely.
 */
public final class CursorShelf {

    private CursorShelf() {}

    public static void runWithEmptyCursor(AbstractContainerMenu menu, MultiPlayerGameMode gameMode,
                                           Player player, Collection<Slot> touched, Runnable action) {
        if (menu.getCarried().isEmpty()) {
            action.run();
            return;
        }

        Slot shelf = park(menu, gameMode, player, touched, menu.getCarried());
        if (shelf == null) return;

        action.run();
        // Cursor is empty again by now: every click sequence this guards
        // drains it fully, onto a slot or back to its source.
        gameMode.handleContainerInput(menu.containerId, shelf.index, 0, ContainerInput.PICKUP, player);
    }

    /**
     * Puts the held stack down on an empty main-inventory or hotbar slot, as
     * {@link #runWithEmptyCursor} sets it aside, and leaves it there. Returns
     * whether the cursor is empty now. Edit mode's entry.
     */
    public static boolean putAway(AbstractContainerMenu menu, MultiPlayerGameMode gameMode, Player player) {
        return menu.getCarried().isEmpty() || park(menu, gameMode, player, List.of(), menu.getCarried()) != null;
    }

    /**
     * Tries each empty, untouched slot of the player's main inventory or
     * hotbar in turn, actually clicking it, until one takes {@code held}.
     * Returns the slot it landed on, or {@code null} if every candidate
     * refused (a lock can veto the click itself, which
     * {@link IPSlotOperations#mayAccept} can't see) or none exists (the
     * inventory is full). Armor and offhand are never candidates: a PICKUP
     * there equips the item rather than merely storing it, which could curse
     * the player with whatever they happened to be holding.
     */
    private static Slot park(AbstractContainerMenu menu, MultiPlayerGameMode gameMode, Player player,
                              Collection<Slot> touched, ItemStack held) {
        for (Slot s : menu.slots) {
            if (s.container != player.getInventory()) continue;
            if (s.getContainerSlot() >= 36) continue;
            if (touched.contains(s)) continue;
            if (!s.getItem().isEmpty()) continue;
            if (!IPSlotOperations.mayAccept(menu, s, held)) continue;
            gameMode.handleContainerInput(menu.containerId, s.index, 0, ContainerInput.PICKUP, player);
            if (menu.getCarried().isEmpty()) return s;
        }
        return null;
    }
}
