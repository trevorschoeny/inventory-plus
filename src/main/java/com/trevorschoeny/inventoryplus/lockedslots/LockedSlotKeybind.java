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
                        if (LockEditMode.isOn()) return; // edit-mode click handles toggling
                        if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;

                        Minecraft mc = Minecraft.getInstance();
                        double mouseX = mc.mouseHandler.xpos()
                                * (double) mc.getWindow().getGuiScaledWidth()
                                / (double) mc.getWindow().getScreenWidth();
                        double mouseY = mc.mouseHandler.ypos()
                                * (double) mc.getWindow().getGuiScaledHeight()
                                / (double) mc.getWindow().getScreenHeight();

                        Slot hovered = slotUnderMouse(currentAcs, mouseX, mouseY);
                        if (hovered != null && LockedSlots.isLockable(hovered)) {
                            LockedSlots.toggle(hovered);
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
