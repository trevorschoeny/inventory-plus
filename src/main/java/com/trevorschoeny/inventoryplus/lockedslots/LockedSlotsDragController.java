package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.config.IPConfig;
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
 * Drag-to-toggle controller for Locked Slots — coerces every slot
 * entered during a drag to the same lock state set on the FIRST
 * touched slot. Two trigger modes:
 *
 * <ul>
 *   <li><b>Edit-mode drag</b> — held LMB. The lock-edit-toggleable set
 *       (inv + hotbar + ender + placed containers), matching edit-mode's
 *       per-slot click scope. Started by
 *       {@link LockedSlotsClickInterceptor}'s allow-click handler
 *       after the first click toggles the initial slot.</li>
 *   <li><b>L-key drag</b> — held L key. Full lockable range (inv +
 *       hotbar + armor + offhand + ender + placed containers). Started by
 *       {@link LockedSlotKeybind}'s after-key-press handler after
 *       the L-press toggles the initial slot. LMB is NOT required —
 *       any cursor motion while L is held coerces entered slots.</li>
 * </ul>
 *
 * <h3>Slot keying</h3>
 *
 * The touched-set keys by {@link Slot#index} (menu-unique), not
 * {@code getContainerSlot()} — in a chest menu the container's slot indices
 * (0-26) overlap the player-inventory portion's, so container-slot keying
 * would conflate a chest slot with a player slot. Coercion routes through
 * {@link LockedSlots#setLockedSlot(Slot, boolean)}, which dispatches to the
 * right store/provider per slot kind.
 *
 * <h3>State semantics</h3>
 *
 * The first slot's NEW state (after the initial toggle) defines the
 * target. Subsequent slots entered are coerced to that target —
 * already-target slots stay (no flip back). Re-entering a slot
 * already touched in the same drag is a no-op (touched-set
 * membership).
 *
 * <h3>Polling cadence</h3>
 *
 * Implemented as a client-tick handler — 20 Hz, so a very fast
 * drag may skip slots between ticks. Acceptable for typical
 * one-direction sweeps across a 9-column row. If it becomes a UX
 * issue, upgrade to {@code Screen.mouseDragged} / {@code mouseMoved}
 * hooks for frame-rate resolution.
 *
 * <h3>Termination</h3>
 *
 * Each tick checks the trigger condition (LMB for EDIT_LMB, L for
 * L_KEY) via GLFW. If the trigger is no longer held, or the open
 * screen is no longer an {@link AbstractContainerScreen}, the drag
 * ends and the touched-set clears.
 */
public final class LockedSlotsDragController {

    private LockedSlotsDragController() {}

    private enum Mode { NONE, EDIT_LMB, L_KEY }

    private static Mode mode = Mode.NONE;
    private static boolean targetLocked;
    /** Menu-unique slot.index values already coerced this drag. */
    private static final Set<Integer> touchedSlots = new HashSet<>();

    public static void startEditModeDrag(int firstSlotMenuIndex, boolean newState) {
        mode = Mode.EDIT_LMB;
        targetLocked = newState;
        touchedSlots.clear();
        touchedSlots.add(firstSlotMenuIndex);
    }

    public static void startLKeyDrag(int firstSlotMenuIndex, boolean newState) {
        mode = Mode.L_KEY;
        targetLocked = newState;
        touchedSlots.clear();
        touchedSlots.add(firstSlotMenuIndex);
    }

    public static void endDrag() {
        mode = Mode.NONE;
        touchedSlots.clear();
    }

    /**
     * True while an L-key drag is in progress. The keybind handler
     * uses this to ignore GLFW key-repeat events — only the initial
     * press should kick off a drag; subsequent auto-repeats while
     * L is held would otherwise re-toggle the slot under the cursor
     * on every repeat tick.
     */
    public static boolean isLKeyDragActive() {
        return mode == Mode.L_KEY;
    }

    /** Registered against {@code ClientTickEvents.END_CLIENT_TICK}. */
    public static void tick(Minecraft mc) {
        if (mode == Mode.NONE) return;
        if (mc == null) {
            endDrag();
            return;
        }

        // Trigger-condition check — ends the drag if the user released
        // the modifier (LMB or L) since the last tick.
        if (mode == Mode.EDIT_LMB && !isMouseHeld(mc, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            endDrag();
            return;
        }
        if (mode == Mode.L_KEY && !isKeyHeld(mc, InputConstants.KEY_L)) {
            endDrag();
            return;
        }

        Screen screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen<?> acs)) {
            // User closed the screen mid-drag.
            endDrag();
            return;
        }

        Slot hovered = slotUnderMouse(acs, mc);
        if (hovered == null) return;
        int key = hovered.index;
        if (touchedSlots.contains(key)) return;

        // Per-mode slot-kind filter. Edit-mode drag matches edit-mode click
        // scope (inv+hotbar + ender + containers); L-drag covers the full
        // lockable range (adds armor / offhand).
        boolean valid = (mode == Mode.EDIT_LMB)
                ? LockedSlots.isEditModeToggleable(hovered)
                : LockedSlots.isLockableHere(hovered);
        if (!valid) return;

        // When cycleSlotsLocked is ON, cycle slots are inert to the lock
        // drag — their lock state is bound to cycle membership and can't
        // be toggled by lock gestures. Add to touched-set so we don't
        // retry on every tick, but don't change lock state.
        if (IPConfig.cycleSlotsLocked() && ColumnCycler.isCycleSlot(hovered)) {
            touchedSlots.add(key);
            return;
        }

        touchedSlots.add(key);
        // Unified dispatch — player + ender → IP client store, containers →
        // the registered provider.
        LockedSlots.setLockedSlot(hovered, targetLocked);
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
