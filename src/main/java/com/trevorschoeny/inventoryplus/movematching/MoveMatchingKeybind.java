package com.trevorschoeny.inventoryplus.movematching;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Screen-scoped {@code M} keybind for triggering move-matching.
 *
 * <p>Single behavior per Trev's 2026-05-16 redirect:
 * <ul>
 *   <li>Mouse over a slot in a <b>targetable</b> slot group when {@code M}
 *       is pressed → trigger move-matching for that group.</li>
 *   <li>Mouse anywhere else (over the widget, over a non-target slot,
 *       over empty UI, over the hotbar) → no-op.</li>
 * </ul>
 *
 * <p>Cycling the cycle stop is now done via <b>Shift + left click on
 * the widget</b> ({@link MoveMatchingWidget#onClick}). The previous
 * keybind-on-button cycle path has been retired.
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} (not a global
 * {@link net.minecraft.client.KeyMapping}) — outside a simplecontainer
 * screen there's no target, so a global binding would steal {@code M}
 * during normal gameplay. Promoting to a rebindable KeyMapping (still
 * scoped) is a polish follow-up.
 */
public final class MoveMatchingKeybind {

    private MoveMatchingKeybind() {}

    private static final int KEY_M = GLFW.GLFW_KEY_M;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        if (event.key() != KEY_M) return;
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
                    });
        });
    }

    /**
     * Walks the screen's targetable slot groups (re-detected fresh — no
     * cached state) and returns the group whose slots contain the mouse,
     * or null.
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
