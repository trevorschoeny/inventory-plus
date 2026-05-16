package com.trevorschoeny.inventoryplus.movematching;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

import org.lwjgl.glfw.GLFW;

/**
 * Two keybind paths for move-matching while a simplecontainer screen is
 * open:
 *
 * <ul>
 *   <li><b>M</b> (default) — execute move-matching with the current
 *       cycle. Wired via {@link ScreenKeyboardEvents} so it fires inside
 *       the open screen without registering a global keybind that would
 *       conflict with the {@code M} world-toggle (only fires in
 *       screens we own).</li>
 *   <li><b>Shift+M</b> (default) — cycle to the next stop without
 *       executing. Stand-in for the spec's "keybind on the button" cycle
 *       path until MK exposes a right-click hook on
 *       {@link com.trevorschoeny.menukit.core.Button}.</li>
 * </ul>
 *
 * <h3>Why screen-keypress events rather than a global KeyMapping</h3>
 *
 * Move-matching only makes sense inside a simplecontainer screen — outside
 * the screen there's no target. Wiring a global {@code KeyMapping} would
 * have the binding consume the {@code M} key during normal gameplay too,
 * stealing it from any future feature (or the player's own {@code M}-bound
 * action). Scoping via {@link ScreenKeyboardEvents} means the key is only
 * captured when one of our target screens is on top.
 *
 * <p>The keybind isn't user-rebindable in the smoke pass; promoting it to
 * a {@code KeyMapping} (still scoped, but exposed in vanilla's Controls
 * menu) is a follow-up.
 */
public final class MoveMatchingKeybind {

    private MoveMatchingKeybind() {}

    /** The default key — letter M. */
    private static final int KEY_M = GLFW.GLFW_KEY_M;

    /**
     * Wires the screen-scoped key listeners on every screen open. Called
     * once at mod init via {@link ScreenEvents#AFTER_INIT}.
     */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!isMoveMatchingScreen(screen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        if (event.key() != KEY_M) return;
                        Minecraft mc = Minecraft.getInstance();
                        boolean shift =
                                (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
                        if (shift) {
                            MoveMatchingButton.cycle(mc);
                        } else {
                            ContainerKey ckey = ContainerKeyResolver.resolve(innerScreen);
                            MoveMatchingCycle cycle = MoveMatchingPrefs.get(ckey);
                            MoveMatchingExecutor.execute(mc, cycle);
                        }
                    });
        });
    }

    /**
     * True for the screen classes we registered the button on. Keeps the
     * keybind scope consistent with the button's coverage.
     *
     * <p>{@link ContainerScreen} covers chests, double chests, ender
     * chests, barrels, trapped chests — every {@code ChestMenu}-backed
     * screen. Vanilla 1.21.11 unified these into one screen class.
     */
    private static boolean isMoveMatchingScreen(Screen screen) {
        return screen instanceof ContainerScreen
                || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen
                || screen instanceof DispenserScreen;
    }
}
