package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Drag-to-toggle controller for Column Cycler: coerces every slot entered
 * during a drag to the cycle-membership state set on the first touched slot.
 * Polled on the client tick; ends when the key is released or the screen closes.
 *
 * <p>Held C key, inventory and hotbar only. Started by {@link ColumnCyclerKeybind}
 * after the initial C press toggles the first slot. There is no LMB drag, since
 * there is no edit mode.
 */
public final class ColumnCyclerDragController {

    private ColumnCyclerDragController() {}

    private enum Mode { NONE, C_KEY }

    private static Mode mode = Mode.NONE;
    private static boolean targetCycle;
    private static final Set<Integer> touchedSlots = new HashSet<>();

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
     * True while a C-key drag is in progress. The keybind handler uses this
     * to ignore key-repeat events, so only the initial press starts a drag.
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

        if (mode == Mode.C_KEY && !isKeyHeld(mc, InputConstants.KEY_C)) {
            endDrag();
            return;
        }

        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) {
            endDrag();
            return;
        }

        Slot hovered = slotUnderMouse(acs);
        if (hovered == null) return;
        int slotIdx = hovered.getContainerSlot();
        if (touchedSlots.contains(slotIdx)) return;

        if (!ColumnCycler.isCycleable(hovered)) return;

        touchedSlots.add(slotIdx);
        ColumnCycler.setCycle(slotIdx, targetCycle);
    }

    private static boolean isKeyHeld(Minecraft mc, int key) {
        return InputConstants.isKeyDown(key);
    }

    /**
     * The slot vanilla highlights under the cursor ({@link ScreenLayout#hoveredSlot}):
     * a created slot ahead of a vanilla one it covers, and the slot's 1-pixel rim
     * included, so the gaps between a grid's slots count too.
     */
    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs) {
        Slot slot = ScreenLayout.hoveredSlot(acs);
        return (slot != null && slot.isActive()) ? slot : null;
    }
}
