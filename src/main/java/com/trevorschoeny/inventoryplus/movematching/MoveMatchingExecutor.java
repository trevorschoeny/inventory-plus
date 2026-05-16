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
import java.util.List;
import java.util.Set;

/**
 * Executes move-matching for a clicked / triggered slot group.
 *
 * <h3>Direction</h3>
 *
 * The clicked group is the <b>target</b>. Items flow INTO it from every
 * other slot in the open menu, with two exclusions:
 *
 * <ul>
 *   <li><b>Hotbar</b> — never a source (spec §"Scope" + Trev's redirect).</li>
 *   <li><b>The target group itself</b> — items already in the target
 *       define the match-set; their own slots aren't sources to themselves.</li>
 * </ul>
 *
 * All other slot groups (player main inv, chest container, ender chest
 * container, hopper, dispenser, furnace fuel, brewing-stand bottles,
 * donkey saddle, etc.) are eligible sources per Trev's permissive-source
 * direction (2026-05-15 redirect).
 *
 * <h3>Match-set</h3>
 *
 * Built from items currently in the target group's slots. Matching is by
 * item identity ({@link Item#equals}, effectively item-ID match) per spec
 * §"What": "matched by item ID (not by name or NBT)".
 *
 * <h3>Cycle stops</h3>
 *
 * <ul>
 *   <li>{@link MoveMatchingCycle#ALL_MATCHING} — every match flows, including
 *       non-stackables.</li>
 *   <li>{@link MoveMatchingCycle#STACKABLE_ONLY} — match-set filtered to
 *       stackable items ({@code maxStackSize > 1}) at both sides
 *       (non-stackable in target doesn't pull anything; non-stackable in
 *       source stays put).</li>
 *   <li>{@link MoveMatchingCycle#DISABLED} — no-op.</li>
 * </ul>
 *
 * <h3>Overflow</h3>
 *
 * Vanilla {@link ClickType#QUICK_MOVE} handles partial-fit naturally —
 * items move what fits and leave the remainder, matching spec
 * §"Destination capacity overflow".
 */
public final class MoveMatchingExecutor {

    private MoveMatchingExecutor() {}

    /**
     * Runs move-matching for the given target slot group with the given
     * cycle. Sources are inferred from the open menu (everything except
     * hotbar and the target group itself).
     */
    public static void execute(Minecraft mc, SlotGroup target, MoveMatchingCycle cycle) {
        if (cycle == MoveMatchingCycle.DISABLED) return;
        if (!target.targetable()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        Inventory playerInv = player.getInventory();

        // ─── Build match set from target's items ──────────────────────────
        Set<Item> matchSet = new HashSet<>();
        for (Slot slot : target.slots()) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stack.getMaxStackSize() <= 1) {
                continue;
            }
            matchSet.add(stack.getItem());
        }

        if (matchSet.isEmpty()) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] target has no items — no-op");
            return;
        }

        // ─── Iterate source slots ─────────────────────────────────────────
        //
        // Sources = every slot in the menu EXCEPT:
        //   • hotbar (player inv container-slot 0-8)
        //   • the target group's own slots
        //
        // Per Trev's redirect, the hotbar exclusion is the ONLY blanket
        // exclusion — non-container-y groups like furnace fuel etc. ARE
        // eligible sources (they just can't be targets themselves).
        int moved = 0;
        for (Slot slot : menu.slots) {
            if (isHotbarSlot(slot, playerInv)) continue;
            if (target.containsMenuIndex(slot.index)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (!matchSet.contains(stack.getItem())) continue;
            if (cycle == MoveMatchingCycle.STACKABLE_ONLY
                    && stack.getMaxStackSize() <= 1) {
                continue;
            }

            // QUICK_MOVE — vanilla's shift-click routes the stack toward
            // the "other half" of the screen, which for a chest/inv split
            // means source half → target half. For more exotic menus
            // (multi-container) vanilla's quickMoveStack on the specific
            // ScreenHandler handles the routing.
            gameMode.handleInventoryMouseClick(
                    menu.containerId,
                    slot.index,
                    /* mouseButton ignored for QUICK_MOVE */ 0,
                    ClickType.QUICK_MOVE,
                    player);
            moved++;
        }

        InventoryPlusClient.LOGGER.debug(
                "[move-matching] target.key={} cycle={} moved {} source slot(s)",
                target.key() != null ? target.key().toKeyString() : "<no-key>",
                cycle, moved);
    }

    /**
     * Hotbar = slots in the player's main inventory with container-slot
     * index in [0, 9). The spec excludes the hotbar entirely from
     * move-matching participation.
     */
    private static boolean isHotbarSlot(Slot slot, Inventory playerInv) {
        if (slot.container != playerInv) return false;
        int ci = slot.getContainerSlot();
        return ci >= 0 && ci < 9;
    }

    /** Unused, kept for documentation of menu-slot bounds during refactors. */
    @SuppressWarnings("unused")
    private static List<Slot> debugUnused() { return List.of(); }
}
