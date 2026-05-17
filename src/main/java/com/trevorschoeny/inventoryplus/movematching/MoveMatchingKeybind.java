package com.trevorschoeny.inventoryplus.movematching;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Screen-scoped {@code I} keybind for Move Matching IN — trigger only.
 *
 * <p>Single behavior (Trev 2026-05-16, click-cycle revision):
 *
 * <ul>
 *   <li>Mouse over a slot in a <b>targetable</b> slot group when {@code I}
 *       is pressed → trigger Move Matching IN for that group with its
 *       current cycle.</li>
 *   <li>Mouse anywhere else (over the widget, over the hotbar, over a
 *       non-target slot, over empty UI) → no-op.</li>
 * </ul>
 *
 * <p>Cycling is now a <b>right-click</b> action on the widget itself —
 * see {@link MoveMatchingWidget#onClick}. The earlier keybind-on-button
 * cycle path is retired so the click and keybind have separate, simpler
 * roles.
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so {@code I} only fires
 * inside simplecontainer screens. Promoting to a rebindable
 * {@link net.minecraft.client.KeyMapping} is filed in DEFERRED.md.
 *
 * <p>Move Matching OUT will get its own sibling keybind ({@code O}) when
 * that feature lands.
 */
public final class MoveMatchingKeybind {

    private MoveMatchingKeybind() {}

    /**
     * Move Matching IN's keybind — {@code I} per the 2026-05-16 spec
     * sweep.
     */
    private static final int KEY_IN = GLFW.GLFW_KEY_I;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        if (event.key() != KEY_IN) return;
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
                            MoveMatchingCycle cycle = MoveMatchingPrefs.get(hoverSlotGroup.key());
                            MoveMatchingExecutor.execute(mc, hoverSlotGroup, cycle);
                        }
                        // Else no-op.
                    });
        });
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
