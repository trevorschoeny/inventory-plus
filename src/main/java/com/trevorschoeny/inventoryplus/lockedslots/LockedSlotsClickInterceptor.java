package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-level mouse handler for Locked Slots.
 *
 * <h3>Edit mode (narrowed per Trev 2026-05-16)</h3>
 *
 * Edit mode only blocks clicks on <b>inventory + hotbar slots</b>
 * (container-slot 0-35) — those are the "personal inventory" Trev's
 * mental model treats as the locked-slots scope. Armor / offhand slots
 * and external container slots stay vanilla-interactable in edit mode.
 * Lock-toggle via click in edit mode applies to inv + hotbar; lock-toggle
 * on armor / offhand is via the {@code L} keybind (works regardless of
 * edit mode).
 *
 * <p>Clicks outside slots (on widgets like the lock-edit button itself,
 * or the MM IN/OUT buttons) pass through to vanilla so widgets work
 * normally — including the lock-edit button click that toggles edit
 * mode OFF.
 *
 * <h3>Manual cursor placement override (non-edit mode)</h3>
 *
 * Normal LMB click on a lockable slot → set
 * {@link ManualPlaceOverride} so {@link
 * com.trevorschoeny.inventoryplus.mixin.SlotMayPlaceMixin} lets vanilla
 * decide (manual placement into locked slots is allowed per spec).
 * Cleared in {@code afterMouseClick}.
 *
 * <h3>Corrector notification</h3>
 *
 * Any manual click on a lockable player slot also records into
 * {@link LockedSlotsCorrector#recordManualClick} so the auto-pickup
 * tick handler doesn't false-positive on a slot the player just touched.
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
                        // Click outside slot bounds — let vanilla / widgets
                        // handle it. THIS IS THE FIX for "can't toggle
                        // edit mode off": the lock-edit button needs to
                        // receive its click to flip the mode.
                        return true;
                    }
                    if (LockedSlots.isInvOrHotbarSlot(hovered)) {
                        // Inv / hotbar click in edit mode → toggle lock,
                        // cancel vanilla item interaction.
                        LockedSlots.toggle(hovered);
                        return false;
                    }
                    // Armor / offhand / external container slot → vanilla
                    // handles normally (per Trev's "still be able to
                    // interact with other containers").
                    return true;
                }

                // Not in edit mode — for manual LMB on a player slot, set
                // the override so mayPlace allows placement into locked
                // slots from this click.
                if (event.button() != 0) return true; // only LMB
                if (hovered != null && LockedSlots.isLockable(hovered)) {
                    ManualPlaceOverride.set();
                    InventoryPlusClient.LOGGER.debug(
                            "[locked-slots] manual override SET for slot {} (LMB)",
                            hovered.getContainerSlot());
                }
                return true;
            });

            ScreenMouseEvents.afterMouseClick(screen).register((s, event, wasHandled) -> {
                ManualPlaceOverride.clear();

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
