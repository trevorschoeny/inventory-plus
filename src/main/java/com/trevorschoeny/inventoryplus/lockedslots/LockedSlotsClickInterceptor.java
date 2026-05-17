package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-level mouse handler for Locked Slots — edit-mode dispatch +
 * manual-click recording for the post-hoc corrector.
 *
 * <h3>Edit mode (narrowed per Trev 2026-05-16)</h3>
 *
 * Edit mode only blocks clicks on <b>inventory + hotbar slots</b>
 * (container-slot 0-35). Armor / offhand and external container slots
 * stay vanilla-interactable in edit mode. Clicks outside slots (e.g.,
 * on the lock-edit button itself, or MM widgets) pass through to vanilla
 * so widgets work — including the lock-edit button's toggle-off click.
 *
 * <h3>Manual-click recording</h3>
 *
 * For the post-hoc {@link LockedSlotsCorrector} to distinguish manual
 * placement from auto-pickup, we record each click on a lockable player
 * slot so the corrector's tick handler doesn't false-positive on a slot
 * the player just touched.
 *
 * <h3>What's NOT here anymore</h3>
 *
 * The old {@code ManualPlaceOverride} thread-local hack was removed —
 * shift-click placement is now blocked by
 * {@link com.trevorschoeny.inventoryplus.mixin.AbstractContainerMenuMoveItemStackToMixin}
 * (scoped exactly to the shift-click path, doesn't affect manual
 * cursor placement). Manual cursor placement works because the mayPlace
 * block was removed entirely.
 */
public final class LockedSlotsClickInterceptor {

    private LockedSlotsClickInterceptor() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                Slot hovered = slotUnderMouse(acs, event.x(), event.y());

                if (LockEditMode.isOn()) {
                    if (hovered == null) {
                        // Click outside slot bounds — let widgets handle it.
                        return true;
                    }
                    if (LockedSlots.isInvOrHotbarSlot(hovered)) {
                        // Inv / hotbar click in edit mode → toggle lock.
                        LockedSlots.toggle(hovered);
                        return false;
                    }
                    // Armor / offhand / external container slot → vanilla
                    // handles normally.
                    return true;
                }

                // Not in edit mode — nothing to do at allow phase.
                return true;
            });

            ScreenMouseEvents.afterMouseClick(screen).register((s, event, wasHandled) -> {
                // Record manual click on lockable player slot so the corrector
                // doesn't false-positive.
                if (event.button() != 0) return true;
                Slot hovered = slotUnderMouse(acs, event.x(), event.y());
                if (hovered != null && LockedSlots.isLockable(hovered)) {
                    LockedSlotsCorrector.recordManualClick(hovered.getContainerSlot());
                }
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
