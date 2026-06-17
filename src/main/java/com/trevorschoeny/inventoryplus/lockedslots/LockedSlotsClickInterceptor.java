package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-level mouse handler for Locked Slots — edit-mode dispatch only.
 *
 * <h3>Edit mode</h3>
 *
 * Edit mode blocks clicks on the <b>lock-toggleable</b> slots — the
 * inv+hotbar subset (container-slot 0-35), the ender chest, and (with IPP)
 * placed-container slots — toggling each slot's lock instead. Armor / offhand
 * stay vanilla-interactable in edit mode (they're lockable via {@code L} only,
 * per Trev 2026-05-16). Clicks outside slots (e.g., on the lock-edit button
 * itself, or MM widgets) pass through to vanilla so widgets work — including
 * the lock-edit button's toggle-off click.
 *
 * <h3>Why no manual-click recording anymore</h3>
 *
 * The post-hoc corrector that needed click-tracking was removed once
 * the mixin chain (move-item-stack-to + get-free-slot) reliably blocks
 * shift-click and auto-pickup at the source. With no corrector
 * second-guessing slot fills, manual cursor placement is naturally
 * unaffected — vanilla {@code Slot.mayPlace} returns true for player
 * slots and no mod code interferes with the {@code PICKUP} click-type
 * path. The fragile "did the player just touch this slot?" detection
 * is gone with the corrector.
 */
public final class LockedSlotsClickInterceptor {

    private LockedSlotsClickInterceptor() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!LockEditMode.isOn()) {
                    // Not in edit mode — let vanilla handle the click. The
                    // mixin chain protects locked slots from shift-click
                    // and auto-pickup; manual placement is allowed by
                    // design and needs no intervention here.
                    return true;
                }
                Slot hovered = slotUnderMouse(acs, event.x(), event.y());
                if (hovered == null) {
                    // Click outside slot bounds — let widgets handle it.
                    return true;
                }
                if (LockedSlots.isEditModeToggleable(hovered)) {
                    // When cycleSlotsLocked is ON, a cycle slot's lock
                    // state is bound to its cycle state — clicks in
                    // lock-edit mode can't toggle it. Consume the click
                    // (return false) so item interaction is still
                    // suppressed; the slot is just inert.
                    if (IPConfig.cycleSlotsLocked() && ColumnCycler.isCycleSlot(hovered)) {
                        return false;
                    }
                    // Inv / hotbar / ender / placed-container click in edit
                    // mode → toggle lock. Unified dispatch routes player +
                    // ender to IP's client store, containers to the provider.
                    LockedSlots.toggleSlot(hovered);
                    boolean newState = LockedSlots.isLockedSlot(hovered);
                    // Start an LMB drag so the user can sweep across
                    // adjacent slots, coercing each to the new state of
                    // the first slot. Other mouse buttons toggle the
                    // single slot but don't drag. Keyed by slot.index
                    // (menu-unique) so a container slot can't collide
                    // with a player slot in the same menu.
                    if (event.button() == 0) {
                        LockedSlotsDragController.startEditModeDrag(hovered.index, newState);
                    }
                    return false;
                }
                // Armor / offhand slot → vanilla handles normally (those are
                // lockable only via L, not edit-mode click).
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
