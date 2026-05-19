package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-level mouse handler for Column Cycler — edit-mode dispatch only.
 *
 * <p>Mirrors {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsClickInterceptor}.
 *
 * <h3>Edit mode</h3>
 *
 * While {@link ColumnCyclerEditMode} is on, clicks on inv + hotbar slots
 * (container-slot 0-35) toggle cycle membership instead of moving items.
 * Clicks outside slots (toolbar buttons, widgets) pass through to vanilla
 * so widgets work — including the cycler-edit button's toggle-off click.
 * Clicks on armor / offhand / external slots also pass through unchanged.
 */
public final class ColumnCyclerClickInterceptor {

    private ColumnCyclerClickInterceptor() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!ColumnCyclerEditMode.isOn()) {
                    // Not in edit mode — let vanilla handle the click.
                    return true;
                }
                Slot hovered = slotUnderMouse(acs, event.x(), event.y());
                if (hovered == null) {
                    // Click outside slot bounds — let widgets handle it.
                    return true;
                }
                if (ColumnCycler.isCycleable(hovered)) {
                    // Inv slot (9-35) in edit mode → toggle cycle membership.
                    int slotIdx = hovered.getContainerSlot();
                    ColumnCycler.toggleByContainerSlot(slotIdx);
                    boolean newState = ColumnCycler.isCycleSlot(slotIdx);
                    if (event.button() == 0) {
                        ColumnCyclerDragController.startEditModeDrag(slotIdx, newState);
                    }
                    return false;
                }
                // Hotbar slot (0-8) — the gray edit-mode overlay shows
                // here too, but the click is inert (can't directly
                // toggle cycle on hotbar; it's derived from the column).
                // Consume the click so the player doesn't accidentally
                // pick up / drop items mid-edit.
                if (LockedSlots.isInvOrHotbarSlot(hovered)) {
                    return false;
                }
                // Armor / offhand / external slot → vanilla handles.
                return true;
            });
        });
    }

    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs,
                                                 double mouseX, double mouseY) {
        int leftPos = ScreenLayout.leftPos(acs);
        int topPos = ScreenLayout.topPos(acs);
        for (Slot slot : acs.getMenu().slots) {
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mouseX >= sx && mouseX < sx + 16
                    && mouseY >= sy && mouseY < sy + 16) {
                return slot;
            }
        }
        return null;
    }
}
