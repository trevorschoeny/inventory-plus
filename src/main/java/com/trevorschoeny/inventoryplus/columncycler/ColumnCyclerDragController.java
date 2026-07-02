package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Drag-to-toggle controller for Column Cycler — coerces every slot
 * entered during a drag to the same cycle-membership state set on the
 * FIRST touched slot. Mirrors
 * {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsDragController}.
 *
 * <ul>
 *   <li><b>Edit-mode drag</b> — held LMB while
 *       {@link ColumnCyclerEditMode} is on. Inv + hotbar only.
 *       Started by {@link ColumnCyclerClickInterceptor}.</li>
 *   <li><b>C-key drag</b> — held C key. Inv + hotbar only. Started by
 *       {@link ColumnCyclerKeybind} after the initial C-press toggles
 *       the first slot.</li>
 * </ul>
 *
 * <p>Same state semantics + polling cadence + termination conditions
 * as the locked-slots drag controller.
 */
public final class ColumnCyclerDragController {

    private ColumnCyclerDragController() {}

    private enum Mode { NONE, EDIT_LMB, C_KEY }

    private static Mode mode = Mode.NONE;
    private static boolean targetCycle;
    private static final Set<Integer> touchedSlots = new HashSet<>();

    public static void startEditModeDrag(int firstSlotContainerIdx, boolean newState) {
        mode = Mode.EDIT_LMB;
        targetCycle = newState;
        touchedSlots.clear();
        touchedSlots.add(firstSlotContainerIdx);
    }

    public static void startCKeyDrag(int firstSlotContainerIdx, boolean newState) {
        mode = Mode.C_KEY;
        targetCycle = newState;
        touchedSlots.clear();
        touchedSlots.add(firstSlotContainerIdx);
    }

    public static void endDrag() {
        mode = Mode.NONE;
        touchedSlots.clear();
    }

    /**
     * True while a C-key drag is in progress. The keybind handler
     * uses this to ignore GLFW key-repeat events — only the initial
     * press should kick off a drag.
     */
    public static boolean isCKeyDragActive() {
        return mode == Mode.C_KEY;
    }

    /** Registered against {@code ClientTickEvents.END_CLIENT_TICK}. */
    public static void tick(Minecraft mc) {
        if (mode == Mode.NONE) return;
        if (mc == null) {
            endDrag();
            return;
        }

        if (mode == Mode.EDIT_LMB && !isMouseHeld(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            endDrag();
            return;
        }
        if (mode == Mode.C_KEY && !isKeyHeld(mc, InputConstants.KEY_C)) {
            endDrag();
            return;
        }

        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) {
            endDrag();
            return;
        }

        Slot hovered = slotUnderMouse(acs, mc);
        if (hovered == null) return;
        int slotIdx = hovered.getContainerSlot();
        if (touchedSlots.contains(slotIdx)) return;

        if (!ColumnCycler.isCycleable(hovered)) return;

        touchedSlots.add(slotIdx);
        ColumnCycler.setCycle(slotIdx, targetCycle);
    }

    private static boolean isMouseHeld(Minecraft mc, int button) {
        long window = mc.getWindow().handle();
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    private static boolean isKeyHeld(Minecraft mc, int key) {
        return InputConstants.isKeyDown(mc.getWindow(), key);
    }

    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs, Minecraft mc) {
        double mouseX = mc.mouseHandler.xpos()
                * (double) mc.getWindow().getGuiScaledWidth()
                / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos()
                * (double) mc.getWindow().getGuiScaledHeight()
                / (double) mc.getWindow().getScreenHeight();
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
