package com.trevorschoeny.inventoryplus.settings;

import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The Open settings key. In the world it is polled on the client tick, the
 * way vanilla's own keys are; on a container screen it is matched on key
 * press, the way Inventory Plus's other screen keys are. Either way the menu
 * returns to where it was opened from.
 */
public final class SettingsKeybind {

    private SettingsKeybind() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (IPKeybinds.OPEN_SETTINGS.consumeClick()) {
                if (mc.gui.screen() == null) SettingsMenu.open(null);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((inner, event) -> {
                if (IPKeybinds.OPEN_SETTINGS.matches(event)) SettingsMenu.open(inner);
            });
        });
    }
}
