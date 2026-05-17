package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingButtons;
import com.trevorschoeny.inventoryplus.movematching.SlotGroup;
import com.trevorschoeny.inventoryplus.movematching.SlotGroupDetector;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import java.util.List;

/**
 * Registers {@link LockEditWidget} on every {@link AbstractContainerScreen}
 * that exposes the player main inventory, per Trev 2026-05-16
 * ("anywhere the player can see the inventory is where it appears").
 *
 * <h3>Screen scope</h3>
 *
 * <ul>
 *   <li>Bare {@code InventoryScreen} → widget visible (alone at the
 *       rightmost spot, slotIndex = 0).</li>
 *   <li>{@code ContainerScreen} / shulker / hopper / dispenser → widget
 *       visible (left of MM widgets, slotIndex = 2 — layout
 *       {@code [LOCK] [OUT] [IN]}).</li>
 *   <li>Specialized UIs that expose the player inv (anvil, furnace,
 *       brewing, etc.) → widget visible (slotIndex = 0).</li>
 *   <li>{@code CreativeModeInventoryScreen} → excluded for the smoke
 *       pass. The creative item picker isn't a real inventory and
 *       per-tab layout differs; revisit if needed.</li>
 * </ul>
 *
 * <p>Edit mode auto-disables on every screen open via
 * {@link LockEditMode#reset}, per the Option-A lifecycle Trev picked.
 */
public final class LockedSlotsButtons {

    private LockedSlotsButtons() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Edit mode resets on every screen open — pre-screen state
            // doesn't leak forward.
            LockEditMode.reset();

            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            if (screen instanceof CreativeModeInventoryScreen) return;

            List<SlotGroup> groups = SlotGroupDetector.detect(screen);
            SlotGroup playerMainInv = MoveMatchingButtons.findPlayerMainInv(groups);
            if (playerMainInv == null) {
                InventoryPlusClient.LOGGER.debug(
                        "[locked-slots] no player main inv group on {} — no lock-edit widget",
                        screen.getClass().getSimpleName());
                return;
            }

            // Slot-index in the right-aligned button row. When MM widgets
            // are present (chest / shulker / hopper / dispenser), they
            // take slots 0 (IN) + 1 (OUT); lock-edit gets slot 2.
            // Otherwise lock-edit is alone at slot 0.
            int slotIndex = SlotGroupDetector.isMoveMatchingScreen(screen) ? 2 : 0;

            Screens.getButtons(screen).add(new LockEditWidget(playerMainInv, acs, slotIndex));
            InventoryPlusClient.LOGGER.debug(
                    "[locked-slots] registered lock-edit widget on {} (slotIndex={})",
                    screen.getClass().getSimpleName(), slotIndex);
        });
    }
}
