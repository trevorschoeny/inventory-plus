package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-level mouse handler for Locked Slots — covers two paths:
 *
 * <ol>
 *   <li><b>Edit mode active</b> → intercept clicks. If the click is on
 *       a lockable slot, toggle its lock. Cancel vanilla regardless
 *       (no item interaction during edit mode per Trev 2026-05-16).</li>
 *   <li><b>Edit mode off + normal LMB click on a player slot</b> → set
 *       {@link ManualPlaceOverride} for the duration of vanilla's
 *       click processing, then clear it after. Combined with the
 *       {@link com.trevorschoeny.inventoryplus.mixin.SlotMayPlaceMixin},
 *       this lets manual cursor placement into locked slots succeed
 *       (allowed per spec) while shift-click placement gets blocked.</li>
 * </ol>
 *
 * <p>Also records manual-click events into
 * {@link LockedSlotsCorrector#recordManualClick} so the auto-pickup
 * corrector's tick handler knows which slots the player just touched
 * and doesn't false-positive on them.
 */
public final class LockedSlotsClickInterceptor {

    private LockedSlotsClickInterceptor() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (LockEditMode.isOn()) {
                    // Edit-mode handling: toggle lock on lockable slot;
                    // cancel vanilla regardless (no item interactions).
                    Slot hovered = slotUnderMouse(acs, event.x(), event.y());
                    if (hovered != null && LockedSlots.isLockable(hovered)) {
                        LockedSlots.toggle(hovered);
                    }
                    return false; // cancel
                }

                // Not in edit mode — for manual LMB on a player slot,
                // set the override so mayPlace allows placement into
                // locked slots from this click.
                if (event.button() != 0) return true; // only LMB
                Slot hovered = slotUnderMouse(acs, event.x(), event.y());
                if (hovered != null && LockedSlots.isLockable(hovered)) {
                    ManualPlaceOverride.set();
                }
                return true; // allow vanilla
            });

            ScreenMouseEvents.afterMouseClick(screen).register((s, event, wasHandled) -> {
                ManualPlaceOverride.clear();

                // Tell the corrector this slot was manually touched so
                // it doesn't false-positive on the post-tick scan.
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
