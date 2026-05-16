package com.trevorschoeny.inventoryplus.movematching;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Screen-scoped {@code M} keybind. Two behaviors, hover-aware:
 *
 * <ul>
 *   <li>Mouse over a {@link MoveMatchingWidget} → <b>cycle</b> that
 *       group's setting one stop.</li>
 *   <li>Mouse over a slot in a <b>targetable</b> slot group → <b>trigger</b>
 *       move-matching for that group with its current cycle.</li>
 *   <li>Mouse over the hotbar, a non-target slot, or empty UI → no-op.</li>
 * </ul>
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} (not a global
 * {@link net.minecraft.client.KeyMapping}) — outside a simplecontainer
 * screen there's no meaningful action, so a global keybind would
 * steal {@code M} during normal gameplay. Promoting to a rebindable
 * KeyMapping is filed in DEFERRED.md.
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

                        // 1. Cycle if hovering one of our widgets.
                        MoveMatchingWidget hoverWidget =
                                widgetUnderMouse(innerScreen, mouseX, mouseY);
                        if (hoverWidget != null) {
                            hoverWidget.cycle();
                            return;
                        }

                        // 2. Trigger if hovering a slot in a targetable group.
                        SlotGroup hoverSlotGroup = slotGroupUnderMouse(acs, mouseX, mouseY);
                        if (hoverSlotGroup != null && hoverSlotGroup.targetable()) {
                            MoveMatchingCycle cycle = MoveMatchingPrefs.get(hoverSlotGroup.key());
                            MoveMatchingExecutor.execute(mc, hoverSlotGroup, cycle);
                        }
                        // 3. Else no-op.
                    });
        });
    }

    /**
     * Returns the {@link MoveMatchingWidget} whose bounds contain the
     * mouse, or null. Iterates the screen's button list (which is where
     * we registered the widgets) so no per-screen state map is needed.
     */
    private static @Nullable MoveMatchingWidget widgetUnderMouse(Screen screen,
                                                                 double mouseX, double mouseY) {
        List<AbstractWidget> buttons = Screens.getButtons(screen);
        for (AbstractWidget w : buttons) {
            if (!(w instanceof MoveMatchingWidget mmw)) continue;
            if (mouseX >= w.getX() && mouseX < w.getX() + w.getWidth()
                    && mouseY >= w.getY() && mouseY < w.getY() + w.getHeight()) {
                return mmw;
            }
        }
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
