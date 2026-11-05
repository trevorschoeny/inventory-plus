package com.trevorschoeny.inventoryplus.hotbarsets;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;

/**
 * The Hotbar Sets key: held, the list is up; the wheel moves the selection; let go, the
 * selected entry is applied. Right-click or Escape while holding cancels, and the list stays
 * down until the key is let go.
 *
 * <p>Works on the HUD and on the player's own inventory screens (survival's and creative's).
 * The key is read as a raw held state ({@link IPKeybinds#isHeld}), so it works on every
 * screen and needs no press event; Tab still shows vanilla's player list too, and the list is
 * drawn in front of it. On a screen the key is kept from vanilla, which would move the focus
 * with Tab, unless a text field has the keys (creative's search).
 *
 * <p>Selection 0 is the hotbar itself: the list opens there, and letting go there does nothing.
 */
public final class HotbarSetsInput {

    private HotbarSetsInput() {}

    private static boolean open = false;
    /** Cancelled this hold: the list stays down until the key is let go. */
    private static boolean cancelled = false;
    private static int selected = 0;

    public static boolean isOpen() { return open; }

    public static int selected() { return selected; }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(HotbarSetsInput::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(HotbarSetsInput::tick);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (open && event.isEscape()) {
                    cancel();
                    return false; // Escape cancels the list, and the screen stays open
                }
                // Tab moves vanilla's focus; the key is ours here unless a text field has it.
                return !(IPConfig.hotbarSetsEnabled() && IPKeybinds.HOTBAR_SETS.matches(event) && !typing(s));
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (open && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    cancel();
                    return false;
                }
                return true;
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, dx, dy) -> !scroll(dy));
        });
    }

    /** Before vanilla reads its keys: a right-click while the list is up cancels, and uses nothing. */
    private static void startTick(Minecraft mc) {
        if (!open) return;
        boolean rightClicked = false;
        while (mc.options.keyUse.consumeClick()) rightClicked = true;
        if (rightClicked) cancel();
    }

    /** Opens the list when the key goes down where it works, and applies the selection when it comes up. */
    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        boolean held = player != null && IPConfig.hotbarSetsEnabled()
                && IPKeybinds.isHeld(IPKeybinds.HOTBAR_SETS) && worksOn(mc.gui.screen());
        if (!held) {
            // Let go: apply what's selected. Left a screen the list works on (the pause menu): cancel.
            if (open && player != null && !IPKeybinds.isHeld(IPKeybinds.HOTBAR_SETS) && worksOn(mc.gui.screen())) {
                HotbarSets.Entry entry = HotbarSets.entryAt(player, selected);
                if (entry != null) HotbarSets.apply(entry);
            }
            open = false;
            cancelled = false;
            return;
        }
        if (!open && !cancelled) {
            open = true;
            selected = 0;
        }
        // The list can shrink under the selection (a set deleted, creative left).
        selected = Math.min(selected, HotbarSets.entries(player).size());
    }

    /** The wheel while the list is up: up moves away from the hotbar. True when it was ours. */
    public static boolean scroll(double dy) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!open || player == null || dy == 0) return false;
        selected = Math.clamp(selected + (dy > 0 ? 1 : -1), 0, HotbarSets.entries(player).size());
        return true;
    }

    private static void cancel() {
        open = false;
        cancelled = true;
    }

    /** No screen, or the player's own inventory with no text field typing. */
    private static boolean worksOn(Screen screen) {
        if (screen == null) return true;
        return (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) && !typing(screen);
    }

    private static boolean typing(Screen screen) {
        return screen.getFocused() instanceof EditBox box && box.isFocused()
                || screen instanceof CreativeModeInventoryScreen cs && cs.isInputCaptured();
    }

    /** For the checks: the list's state as a hold would leave it. */
    public static void setForChecks(boolean isOpen, int sel) {
        open = isOpen;
        selected = sel;
        cancelled = false;
    }
}
