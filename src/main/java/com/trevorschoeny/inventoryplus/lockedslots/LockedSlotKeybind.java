package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.lwjgl.glfw.GLFW;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-scoped {@code L} keybind — toggles the lock state on the
 * lockable player slot currently under the cursor.
 *
 * <p>No-op when {@link LockEditMode} is on — edit mode uses click-to-
 * toggle instead; L is redundant during edit mode.
 *
 * <p>Hovering a non-lockable slot (crafting input, anvil input, etc.)
 * or empty UI → no-op.
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so {@code L} only fires
 * inside container screens. Promoting to a rebindable
 * {@link net.minecraft.client.KeyMapping} is filed in DEFERRED.md
 * alongside the I/O/S keybinds.
 */
public final class LockedSlotKeybind {

    private LockedSlotKeybind() {}

    private static final int KEY_L = GLFW.GLFW_KEY_L;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        if (event.key() != KEY_L) return;
                        // GLFW auto-repeat fires afterKeyPress every repeat
                        // tick while L is held; ignore once an L-drag is
                        // active so we don't re-toggle the same slot on
                        // every repeat. The tick handler clears the drag
                        // on key release.
                        if (LockedSlotsDragController.isLKeyDragActive()) return;
                        // L works regardless of edit mode — edit-mode click
                        // covers inv + hotbar only, so armor / offhand can
                        // only be locked via L. Keeping L always-on lets
                        // the player lock armor / offhand without exiting
                        // edit mode.
                        if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;

                        Minecraft mc = Minecraft.getInstance();
                        double mouseX = mc.mouseHandler.xpos()
                                * (double) mc.getWindow().getGuiScaledWidth()
                                / (double) mc.getWindow().getScreenWidth();
                        double mouseY = mc.mouseHandler.ypos()
                                * (double) mc.getWindow().getGuiScaledHeight()
                                / (double) mc.getWindow().getScreenHeight();

                        Slot hovered = slotUnderMouse(currentAcs, mouseX, mouseY);
                        if (hovered == null || !LockedSlots.isLockable(hovered)) return;
                        int slotIdx = hovered.getContainerSlot();
                        LockedSlots.toggleByContainerSlot(slotIdx);
                        // Start an L-drag in non-edit mode so the user can
                        // hold L and sweep the cursor across more slots,
                        // coercing each to the first slot's new state. In
                        // edit mode the drag is the LMB-drag mechanic;
                        // L stays a single-slot toggle for armor/offhand
                        // reach.
                        if (!LockEditMode.isOn()) {
                            boolean newState = LockedSlots.isLocked(slotIdx);
                            LockedSlotsDragController.startLKeyDrag(slotIdx, newState);
                        }
                    });
        });
    }

    /**
     * Walks the menu's slots and returns the one whose screen-space
     * bounds contain the mouse, or null. Uses {@link ScreenLayout} for
     * the screen origin (reflection-cached leftPos/topPos).
     */
    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs,
                                                 double mouseX, double mouseY) {
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
