package com.trevorschoeny.inventoryplus.movematching;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Screen-scoped {@code I} / {@code O} keybinds for Move Matching
 * IN / OUT — trigger only (cycling is right-click on the widget).
 *
 * <p>Single behavior per key:
 *
 * <ul>
 *   <li>Mouse over a slot in a <b>targetable</b> slot group when
 *       {@code I} is pressed → trigger Move Matching IN for that
 *       group with its current cycle.</li>
 *   <li>Same for {@code O} → triggers Move Matching OUT.</li>
 *   <li>Mouse anywhere else (over the widget, over the hotbar, over a
 *       non-target slot, over empty UI) → no-op.</li>
 * </ul>
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so the keys only fire
 * inside simplecontainer screens — outside those screens there's no
 * meaningful action, and a global keybind would steal {@code I} /
 * {@code O} during normal gameplay. Promoting to rebindable
 * {@link net.minecraft.client.KeyMapping}s is filed in DEFERRED.md.
 */
public final class MoveMatchingKeybind {

    private MoveMatchingKeybind() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        Direction direction = directionForKey(event.key());
                        if (direction == null) return;
                        if (!(innerScreen instanceof AbstractContainerScreen<?> acs)) return;
                        Minecraft mc = Minecraft.getInstance();
                        double mouseX = mc.mouseHandler.xpos()
                                * (double) mc.getWindow().getGuiScaledWidth()
                                / (double) mc.getWindow().getScreenWidth();
                        double mouseY = mc.mouseHandler.ypos()
                                * (double) mc.getWindow().getGuiScaledHeight()
                                / (double) mc.getWindow().getScreenHeight();

                        SlotGroup hoverSlotGroup = slotGroupUnderMouse(acs, mouseX, mouseY);
                        if (hoverSlotGroup != null && hoverSlotGroup.targetable()) {
                            MoveMatchingCycle cycle =
                                    MoveMatchingPrefs.get(hoverSlotGroup.key(), direction);
                            MoveMatchingExecutor.execute(mc, hoverSlotGroup, direction, cycle);
                        }
                        // Else no-op.
                    });
        });
    }

    /**
     * Maps a GLFW key code to its corresponding {@link Direction}, or
     * null when the key isn't a Move Matching trigger.
     */
    private static @Nullable Direction directionForKey(int key) {
        if (key == GLFW.GLFW_KEY_I) return Direction.IN;
        if (key == GLFW.GLFW_KEY_O) return Direction.OUT;
        return null;
    }

    /**
     * Re-detects slot groups on each keypress (cheap) and returns the
     * targetable group whose slots contain the mouse, or null.
     */
    private static SlotGroup slotGroupUnderMouse(AbstractContainerScreen<?> acs,
                                                 double mouseX, double mouseY) {
        int leftPos = ScreenLayout.leftPos(acs);
        int topPos = ScreenLayout.topPos(acs);
        List<SlotGroup> groups = SlotGroupDetector.detect(acs);
        for (SlotGroup g : groups) {
            if (!g.targetable()) continue;
            for (Slot slot : g.slots()) {
                int sx = leftPos + slot.x;
                int sy = topPos + slot.y;
                if (mouseX >= sx && mouseX < sx + 16
                        && mouseY >= sy && mouseY < sy + 16) {
                    return g;
                }
            }
        }
        return null;
    }
}
