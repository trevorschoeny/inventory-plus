package com.trevorschoeny.inventoryplus.movematching;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Screen-scoped {@code M} handling for move-matching, hover-aware per
 * Trev's 2026-05-15 redirect:
 *
 * <ul>
 *   <li>Mouse over a {@link PngMoveMatchingButton} when {@code M} is
 *       pressed → <b>cycle</b> that slot group's setting.</li>
 *   <li>Mouse over a slot in a <b>targetable</b> slot group → <b>trigger</b>
 *       move-matching for that group.</li>
 *   <li>Mouse anywhere else (over a non-target slot, over empty UI, over
 *       the hotbar, over a specialized slot like furnace fuel) → no-op.</li>
 * </ul>
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
                        Minecraft mc = Minecraft.getInstance();
                        double mouseX = mc.mouseHandler.xpos()
                                * (double) mc.getWindow().getGuiScaledWidth()
                                / (double) mc.getWindow().getScreenWidth();
                        double mouseY = mc.mouseHandler.ypos()
                                * (double) mc.getWindow().getGuiScaledHeight()
                                / (double) mc.getWindow().getScreenHeight();

                        // 1. Cycle if hovering one of our buttons.
                        SlotGroup hoverButtonGroup =
                                MoveMatchingButtons.buttonUnderMouse(innerScreen, mouseX, mouseY);
                        if (hoverButtonGroup != null) {
                            MoveMatchingButtons.cycle(hoverButtonGroup);
                            return;
                        }

                        // 2. Trigger if hovering a slot in a targetable group.
                        if (!(innerScreen instanceof AbstractContainerScreen<?> acs)) return;
                        SlotGroup hoverSlotGroup = slotGroupUnderMouse(acs, mouseX, mouseY);
                        if (hoverSlotGroup != null && hoverSlotGroup.targetable()) {
                            MoveMatchingCycle cycle = MoveMatchingPrefs.get(hoverSlotGroup.key());
                            MoveMatchingExecutor.execute(mc, hoverSlotGroup, cycle);
                        }
                        // 3. Else no-op — hotbar / non-target group / empty UI.
                    });
        });
    }

    /**
     * Walks the screen's tracked slot groups and returns the one whose
     * slots' bounds contain the mouse, or null.
     */
    private static SlotGroup slotGroupUnderMouse(AbstractContainerScreen<?> acs,
                                                 double mouseX, double mouseY) {
        int leftPos = ScreenLayout.leftPos(acs);
        int topPos = ScreenLayout.topPos(acs);
        List<MoveMatchingButtons.Pair> pairs = MoveMatchingButtons.pairsFor(acs);
        for (MoveMatchingButtons.Pair p : pairs) {
            for (Slot slot : p.group().slots()) {
                int sx = leftPos + slot.x;
                int sy = topPos + slot.y;
                if (mouseX >= sx && mouseX < sx + 16
                        && mouseY >= sy && mouseY < sy + 16) {
                    return p.group();
                }
            }
        }
        return null;
    }
}
