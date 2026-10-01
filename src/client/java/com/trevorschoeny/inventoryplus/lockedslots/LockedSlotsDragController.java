package com.trevorschoeny.inventoryplus.lockedslots;

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
 * Hold {@code L} and sweep: every slot the cursor enters gets the state the
 * first press left on the first slot, the active group or no lock. Covers the
 * full lockable range (inventory, hotbar, armor, offhand, ender chest, created
 * slots and placed containers). LMB is not required: any cursor motion while
 * {@code L} is held applies.
 *
 * <p>Slots are keyed by {@link Slot#index} (menu-unique), not
 * {@code getContainerSlot()}: in a chest menu the container's indices overlap
 * the player-inventory portion's. A slot entered twice in one drag is left
 * alone.
 *
 * <p>Polled on the client tick (20 Hz), so a very fast drag may skip slots.
 * Ends when {@code L} is released or the screen closes.
 */
public final class LockedSlotsDragController {

    private LockedSlotsDragController() {}

    private static boolean active;
    /** The group every entered slot gets, or {@code null} to unlock them. */
    private static @Nullable String targetGroup;
    /** Menu-unique slot.index values already coerced this drag. */
    private static final Set<Integer> touchedSlots = new HashSet<>();

    public static void startLKeyDrag(int firstSlotMenuIndex, @Nullable String groupId) {
        active = true;
        targetGroup = groupId;
        touchedSlots.clear();
        touchedSlots.add(firstSlotMenuIndex);
    }

    public static void endDrag() {
        active = false;
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
        return active;
    }

    /** Registered against {@code ClientTickEvents.END_CLIENT_TICK}. */
    public static void tick(Minecraft mc) {
        if (!active) return;
        if (mc == null) {
            endDrag();
            return;
        }

        if (!isKeyHeld(mc, InputConstants.KEY_L)) {
            endDrag();
            return;
        }

        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) {
            // User closed the screen mid-drag.
            endDrag();
            return;
        }

        Slot hovered = slotUnderMouse(acs, mc);
        if (hovered == null) return;
        int key = hovered.index;
        if (touchedSlots.contains(key)) return;

        if (!LockedSlots.isLockableHere(hovered)) return;

        touchedSlots.add(key);
        LockedSlots.setLockedSlot(hovered, targetGroup);
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
