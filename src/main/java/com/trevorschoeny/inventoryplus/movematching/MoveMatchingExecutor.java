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

import java.util.HashSet;
import java.util.Set;

/**
 * The actual move logic for move-matching.
 *
 * <h3>What "matching" means</h3>
 *
 * Per spec §"What": items match by item ID (not by name or NBT). The
 * match set is built from the items currently in the <b>target</b>
 * container; we walk the source slots and shift-click anything whose
 * item is in that set into the target.
 *
 * <h3>Target vs source</h3>
 *
 * Target = the open simplecontainer (chest, shulker, etc.) — equivalently,
 * every slot in the open menu whose backing {@link net.minecraft.world.Container}
 * is NOT the player's inventory.
 *
 * <p>Source = the player's main inventory (3×9 grid, slots 9-35 in
 * {@link Inventory#getItem}). The hotbar is excluded per spec §"Scope".
 *
 * <p>For the bare {@link net.minecraft.client.gui.screens.inventory.InventoryScreen}
 * case (no separate container open), source = {nothing visible} →
 * execution is a no-op. We don't even register the button on
 * InventoryScreen for the smoke pass, so this path is unused, but the
 * executor handles it cleanly if it gets invoked.
 *
 * <h3>Cycle stops</h3>
 *
 * <ul>
 *   <li>{@link MoveMatchingCycle#ALL_MATCHING} — every matching item flows
 *       (including tools, totems, armor).</li>
 *   <li>{@link MoveMatchingCycle#STACKABLE_ONLY} — match-set filtered to
 *       items with {@code maxStackSize > 1}; non-stackables stay put.</li>
 *   <li>{@link MoveMatchingCycle#DISABLED} — no-op.</li>
 * </ul>
 *
 * <h3>Overflow + locked-slots</h3>
 *
 * Vanilla's {@link ClickType#QUICK_MOVE} handles partial-fit naturally —
 * items move what fits and leave the rest where they were, matching spec
 * §"Destination capacity overflow".
 *
 * <p>Locked-slots isn't in 18b, so we don't filter out any source slots
 * or skip any target slots. Spec §"Locked slots" treatment is filed for
 * the locked-slots feature's own implementation pass.
 */
public final class MoveMatchingExecutor {

    private MoveMatchingExecutor() {}

    /**
     * Runs move-matching against the player's current open menu with the
     * given cycle setting.
     *
     * @param mc      Minecraft instance (held to get player + gameMode +
     *                containerMenu)
     * @param cycle   the cycle stop in effect for the current container
     */
    public static void execute(Minecraft mc, MoveMatchingCycle cycle) {
        if (cycle == MoveMatchingCycle.DISABLED) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) {
            // No external container open → no source/target distinction →
            // skip. (InventoryScreen path; matched at registration time
            // by not registering the button there, but defensive here too.)
            return;
        }

        Inventory playerInv = player.getInventory();

        // Build the match set from the target's slots.
        Set<Item> matchSet = new HashSet<>();
        for (Slot slot : menu.slots) {
            if (slot.container == playerInv) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stack.getMaxStackSize() <= 1) {
                // Stackable-only mode: non-stackables don't contribute to
                // the match set either (a single non-stackable in the chest
                // doesn't pull other non-stackables from inventory).
                continue;
            }
            matchSet.add(item);
        }

        if (matchSet.isEmpty()) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] no items in target container — no-op");
            return;
        }

        // Iterate source slots (player main inventory: container index
        // 9-35). Shift-click each match.
        int moved = 0;
        for (Slot slot : menu.slots) {
            if (slot.container != playerInv) continue;
            int containerSlot = slot.getContainerSlot();
            // Hotbar = container slots 0-8. Skip per spec.
            if (containerSlot < 9 || containerSlot >= 36) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (!matchSet.contains(item)) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stack.getMaxStackSize() <= 1) {
                continue;
            }

            // QUICK_MOVE shifts this slot's stack toward the target half
            // (the open container side, since this slot is on the player
            // inv side of the screen). Vanilla handles partial-fit and
            // empty-slot routing.
            gameMode.handleInventoryMouseClick(
                    menu.containerId,
                    slot.index,
                    /* mouseButton ignored for QUICK_MOVE */ 0,
                    ClickType.QUICK_MOVE,
                    player);
            moved++;
        }

        InventoryPlusClient.LOGGER.debug(
                "[move-matching] cycle={} moved {} source slot(s) into target",
                cycle, moved);
    }
}
