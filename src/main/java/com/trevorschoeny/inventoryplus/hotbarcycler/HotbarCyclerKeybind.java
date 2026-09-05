package com.trevorschoeny.inventoryplus.hotbarcycler;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Forward / backward row-cycle keybinds, defaults {@code ]} and
 * {@code [}. Mirrors {@code ColumnCyclerRotationKeybind}'s two-context
 * shape, with one deliberate difference.
 *
 * <h3>No hover scoping</h3>
 *
 * Column Cycler fires in a screen only while hovering a slot in the
 * column it would rotate, because "which column" is otherwise ambiguous.
 * Hotbar Cycler operates on whole rows, so there is nothing to
 * disambiguate and the spec says it fires anywhere in the inventory. No
 * hit-testing here.
 *
 * <h3>Container screens</h3>
 *
 * The keybinds stay live with a chest open and still act on the player's
 * rows, never the container's. That falls out for free: rotation
 * addresses player container-slot indices, which
 * {@code ColumnCyclerRotator.rotateSlots} resolves against whatever menu
 * is open. A chest menu still carries the player's inventory slots, so
 * the same indices resolve and the chest's own slots are never named.
 *
 * <h3>Drain-on-screen-open</h3>
 *
 * Same reason as Column Cycler: the tick handler drains the click queue
 * while a screen is open without firing, so presses made in a screen
 * don't replay when the player closes back to the HUD. The in-screen
 * path uses {@code afterKeyPress} and does not consume the queue.
 */
public final class HotbarCyclerKeybind {

    private HotbarCyclerKeybind() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((innerScreen, event) -> {
                if (!IPConfig.hotbarCyclerEnabled()) return;
                if (!(innerScreen instanceof AbstractContainerScreen<?>)) return;
                boolean forward = IPKeybinds.HOTBAR_CYCLE_FORWARD.matches(event);
                boolean backward = !forward && IPKeybinds.HOTBAR_CYCLE_BACKWARD.matches(event);
                if (!forward && !backward) return;
                HotbarCycler.rotate(forward
                        ? ColumnCyclerRotator.Direction.FORWARD
                        : ColumnCyclerRotator.Direction.BACKWARD);
            });
        });
    }

    /** Drains queued clicks and fires rotation in HUD context. */
    public static void tick(Minecraft mc) {
        boolean firedForward = false;
        while (IPKeybinds.HOTBAR_CYCLE_FORWARD.consumeClick()) {
            if (canFireHud(mc) && !firedForward) {
                HotbarCycler.rotate(ColumnCyclerRotator.Direction.FORWARD);
                firedForward = true;
            }
        }
        boolean firedBackward = false;
        while (IPKeybinds.HOTBAR_CYCLE_BACKWARD.consumeClick()) {
            if (canFireHud(mc) && !firedBackward) {
                HotbarCycler.rotate(ColumnCyclerRotator.Direction.BACKWARD);
                firedBackward = true;
            }
        }
    }

    private static boolean canFireHud(Minecraft mc) {
        return mc != null
                && mc.player != null
                && mc.gui.screen() == null
                && IPConfig.hotbarCyclerEnabled();
    }
}
