package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-level mouse handler for Locked Slots — edit-mode dispatch only.
 *
 * <h3>Edit mode (narrowed per Trev 2026-05-16)</h3>
 *
 * Edit mode only blocks clicks on <b>inventory + hotbar slots</b>
 * (container-slot 0-35). Armor / offhand and external container slots
 * stay vanilla-interactable in edit mode. Clicks outside slots (e.g.,
 * on the lock-edit button itself, or MM widgets) pass through to vanilla
 * so widgets work — including the lock-edit button's toggle-off click.
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
                if (LockedSlots.isInvOrHotbarSlot(hovered)) {
                    // Inv / hotbar click in edit mode → toggle lock.
                    LockedSlots.toggle(hovered);
                    return false;
                }
                // Armor / offhand / external container slot → vanilla
                // handles normally.
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
